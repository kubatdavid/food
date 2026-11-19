<?php
// Domácí spíž (Home Pantry) API — food.force.local
// Storage: JSON files (flock-guarded). Recipes: Markdown files dropped into RECIPES.
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

const DATA    = '/var/www/food/data/pantry.json';
const NAMES   = '/var/www/food/data/names.json';   // how often each item name was added (autocomplete ranking)
const SHOP    = '/var/www/food/data/shopping.json'; // shopping list: [{id, name, qty, unit, category, done, addedAt}]
const RECIPES = '/var/www/food/recipes';
const UNITS   = ['g', 'kg', 'ml', 'l', 'pcs', 'pack', 'tbsp', 'tsp'];
const CATS    = ['Fridge', 'Freezer', 'Pantry', 'Fruit & veg', 'Spices & herbs', 'Other'];

function out(array $data, int $code = 200): never
{
    http_response_code($code);
    echo json_encode($data, JSON_UNESCAPED_UNICODE);
    exit;
}

function fail(string $msg, int $code = 400): never
{
    out(['error' => $msg], $code);
}

/** Run $fn(&$data) on a JSON file under a lock; write back when $write. */
function with_file(string $path, callable $fn, bool $write): mixed
{
    $fh = @fopen($path, 'c+');
    if (!$fh) fail('Soubor se zásobami nejde na serveru otevřít.', 500);
    flock($fh, $write ? LOCK_EX : LOCK_SH);
    $raw = stream_get_contents($fh);
    $data = $raw ? json_decode($raw, true) : [];
    if (!is_array($data)) $data = [];
    try {
        $res = $fn($data);
        if ($write) {
            ftruncate($fh, 0);
            rewind($fh);
            fwrite($fh, json_encode(array_is_list($data) ? array_values($data) : $data, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
            fflush($fh);
        }
    } finally {
        flock($fh, LOCK_UN);
        fclose($fh);
    }
    return $res;
}

function clean(array $in, array $base = []): array
{
    $it = $base;
    if (array_key_exists('name', $in)) {
        $n = trim((string)preg_replace('/\s+/u', ' ', (string)$in['name']));
        if ($n === '' || mb_strlen($n) > 80) fail('Název položky musí mít 1 až 80 znaků.');
        $it['name'] = $n;
    }
    if (array_key_exists('qty', $in)) {
        if (!is_numeric($in['qty'])) fail('Množství musí být číslo.');
        $it['qty'] = max(0, round((float)$in['qty'], 2));
    }
    if (array_key_exists('unit', $in)) {
        if (!in_array($in['unit'], UNITS, true)) fail('Neznámá jednotka.');
        $it['unit'] = $in['unit'];
    }
    if (array_key_exists('category', $in)) {
        $it['category'] = in_array($in['category'], CATS, true) ? $in['category'] : 'Other';
    }
    if (array_key_exists('expires', $in)) {
        $e = $in['expires'];
        if ($e === null || $e === '') $it['expires'] = null;
        elseif (is_string($e) && preg_match('/^\d{4}-\d{2}-\d{2}$/', $e)) $it['expires'] = $e;
        else fail('Datum spotřeby musí mít tvar 2026-12-31.');
    }
    $it['updatedAt'] = gmdate('c');
    return $it;
}

function find_index(array $items, string $id): int
{
    foreach ($items as $i => $it) if (($it['id'] ?? '') === $id) return $i;
    return -1;
}

/** Remember that this name was added, with its latest unit and category. */
function bump_name(array $it): void
{
    with_file(NAMES, function (array &$names) use ($it) {
        $key = mb_strtolower($it['name']);
        $prev = $names[$key] ?? ['count' => 0];
        $names[$key] = [
            'name' => $it['name'],
            'count' => ($prev['count'] ?? 0) + 1,
            'unit' => $it['unit'],
            'qty' => $it['qty'] ?? null,          // amount added last time = the package size the user buys
            'category' => $it['category'] ?? 'Other',
            'last' => gmdate('c'),
        ];
    }, true);
}

/** Add $new to the pantry list, merging into an item with the same name + unit. */
function add_to_pantry(array &$items, array $new): void
{
    foreach ($items as &$it) {
        if (mb_strtolower($it['name']) === mb_strtolower($new['name']) && $it['unit'] === $new['unit']) {
            $it['qty'] = round(($it['qty'] ?? 0) + $new['qty'], 2);
            if (!empty($new['expires'])) $it['expires'] = $new['expires'];
            $it['updatedAt'] = $new['updatedAt'];
            return;
        }
    }
    unset($it);
    $new['id'] = bin2hex(random_bytes(8));
    $items[] = $new;
}

$r      = $_GET['r'] ?? '';
$id     = (string)($_GET['id'] ?? '');
$method = $_SERVER['REQUEST_METHOD'];

if ($method !== 'GET') {
    // Simple CSRF guard: browsers won't send this header cross-site without a preflight.
    if (($_SERVER['HTTP_X_PANTRY'] ?? '') !== '1') fail('Chybí hlavička požadavku.', 403);
    $body = json_decode(file_get_contents('php://input') ?: 'null', true);
    if (!is_array($body)) $body = [];
}

$list = fn() => with_file(DATA, fn(array &$items) => array_values($items), false);

if ($r === 'items') {
    switch ($method) {
        case 'GET':
            out(['items' => $list()]);

        case 'POST': // add, or merge into an existing item with the same name + unit
            $new = clean($body + ['category' => 'Pantry', 'expires' => null, 'unit' => 'g', 'qty' => 0]);
            if (!isset($new['name'])) fail('Chybí název položky.');
            $items = with_file(DATA, function (array &$items) use ($new) {
                add_to_pantry($items, $new);
                return array_values($items);
            }, true);
            bump_name($new);
            out(['items' => $items]);

        case 'PATCH':
            $items = with_file(DATA, function (array &$items) use ($id, $body) {
                $i = find_index($items, $id);
                if ($i < 0) fail('Tahle položka už neexistuje.', 404);
                $items[$i] = clean($body, $items[$i]);
                return array_values($items);
            }, true);
            out(['items' => $items]);

        case 'DELETE':
            $items = with_file(DATA, function (array &$items) use ($id) {
                $i = find_index($items, $id);
                if ($i >= 0) array_splice($items, $i, 1);
                return array_values($items);
            }, true);
            out(['items' => $items]);
    }
    fail('Metoda není povolená.', 405);
}

if ($r === 'shopping') {
    $shopList = fn() => is_file(SHOP) ? with_file(SHOP, fn(array &$s) => array_values($s), false) : [];
    switch ($method) {
        case 'GET':
            out(['shopping' => $shopList()]);

        case 'POST': // add to the list, merging with a not-yet-bought entry of the same name + unit
            $new = clean($body + ['category' => 'Other', 'unit' => 'g', 'qty' => 0]);
            if (!isset($new['name'])) fail('Chybí název položky.');
            unset($new['expires']);
            $shop = with_file(SHOP, function (array &$s) use ($new) {
                foreach ($s as &$it) {
                    if (empty($it['done']) && mb_strtolower($it['name']) === mb_strtolower($new['name']) && $it['unit'] === $new['unit']) {
                        $it['qty'] = round(($it['qty'] ?? 0) + $new['qty'], 2);
                        $it['updatedAt'] = $new['updatedAt'];
                        return array_values($s);
                    }
                }
                unset($it);
                $new['id'] = bin2hex(random_bytes(8));
                $new['done'] = false;
                $new['addedAt'] = $new['updatedAt'];
                $s[] = $new;
                return array_values($s);
            }, true);
            out(['shopping' => $shop]);

        case 'PATCH': // edit an entry or tick it off ({done: true|false})
            $shop = with_file(SHOP, function (array &$s) use ($id, $body) {
                $i = find_index($s, $id);
                if ($i < 0) fail('Tahle položka už na seznamu není.', 404);
                $done = array_key_exists('done', $body) ? (bool)$body['done'] : ($s[$i]['done'] ?? false);
                unset($body['expires']);
                $s[$i] = clean($body, $s[$i]);
                $s[$i]['done'] = $done;
                return array_values($s);
            }, true);
            out(['shopping' => $shop]);

        case 'DELETE': // one entry (?id=…) or every ticked entry (?done=1)
            $onlyDone = ($_GET['done'] ?? '') === '1';
            $shop = with_file(SHOP, function (array &$s) use ($id, $onlyDone) {
                if ($onlyDone) $s = array_values(array_filter($s, fn($it) => empty($it['done'])));
                else { $i = find_index($s, $id); if ($i >= 0) array_splice($s, $i, 1); }
                return array_values($s);
            }, true);
            out(['shopping' => $shop]);
    }
    fail('Metoda není povolená.', 405);
}

// Move every ticked shopping entry into the pantry (merging amounts) and take it off the list.
if ($r === 'shopping-to-pantry' && $method === 'POST') {
    $bought = with_file(SHOP, function (array &$s) {
        $done = array_values(array_filter($s, fn($it) => !empty($it['done'])));
        $s = array_values(array_filter($s, fn($it) => empty($it['done'])));
        return $done;
    }, true);
    $now = gmdate('c');
    $items = with_file(DATA, function (array &$items) use ($bought, $now) {
        foreach ($bought as $b) {
            add_to_pantry($items, ['name' => $b['name'], 'qty' => (float)($b['qty'] ?? 0), 'unit' => $b['unit'],
                'category' => $b['category'] ?? 'Other', 'expires' => null, 'updatedAt' => $now]);
        }
        return array_values($items);
    }, true);
    foreach ($bought as $b) bump_name(['name' => $b['name'], 'qty' => $b['qty'] ?? null, 'unit' => $b['unit'], 'category' => $b['category'] ?? 'Other']);
    $shop = with_file(SHOP, fn(array &$s) => array_values($s), false);
    out(['items' => $items, 'shopping' => $shop, 'moved' => count($bought)]);
}

if ($r === 'names' && $method === 'GET') {
    $names = is_file(NAMES) ? with_file(NAMES, fn(array &$n) => array_values($n), false) : [];
    out(['names' => $names]);
}

if ($r === 'recipes' && $method === 'GET') {
    $rows = [];
    foreach (glob(RECIPES . '/*.md') ?: [] as $f) {
        $slug = basename($f, '.md');
        if (!preg_match('/^[a-z0-9-]+$/', $slug)) continue;
        $title = $slug;
        $fh = fopen($f, 'r');
        while ($fh && ($line = fgets($fh)) !== false) {
            if (preg_match('/^#\s+(.+)/u', $line, $m)) { $title = trim($m[1]); break; }
        }
        if ($fh) fclose($fh);
        $rows[] = ['slug' => $slug, 'title' => $title, 'updated' => gmdate('c', filemtime($f))];
    }
    usort($rows, fn($a, $b) => strcmp($b['updated'], $a['updated']));
    out(['recipes' => $rows]);
}

if ($r === 'recipe' && $method === 'GET') {
    $slug = (string)($_GET['slug'] ?? '');
    if (!preg_match('/^[a-z0-9-]+$/', $slug)) fail('Neplatný název receptu.');
    $f = RECIPES . "/$slug.md";
    if (!is_file($f)) fail('Recept nenalezen.', 404);
    out(['slug' => $slug, 'text' => file_get_contents($f)]);
}

fail('Nenalezeno.', 404);
