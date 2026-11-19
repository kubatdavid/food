<?php
// Public recipe pages for food.abhishekam.cz/recepty/… (the only public part of the pantry site).
// Caddy rewrites /recepty and /recepty/<slug> to /public.php?p=<path>; every other public path is a 404 there.
// Each page carries schema.org/Recipe JSON-LD so Cookidoo's "Importovat recept" can read ingredients and steps.
declare(strict_types=1);

const RECIPES = '/var/www/food/recipes';

header('Content-Type: text/html; charset=utf-8');
header('X-Robots-Tag: noindex, nofollow');
header('Referrer-Policy: no-referrer');

function h(string $s): string { return htmlspecialchars($s, ENT_QUOTES | ENT_HTML5, 'UTF-8'); }

/** Markdown inline → plain text (for JSON-LD). */
function plain(string $s): string
{
    $s = preg_replace('/\s+–\s+\*[^*]+\*\s*$/u', '', $s);   // trailing "– *koupit*" notes
    $s = str_replace(['**', '`'], '', $s);
    $s = preg_replace('/(?<!\w)\*([^*]+)\*(?!\w)/u', '$1', $s);
    return trim((string)$s);
}

/** Markdown inline → HTML (bold Thermomix settings get a chip). */
function inline(string $s): string
{
    $s = preg_replace('/\s+–\s+\*[^*]+\*\s*$/u', '', $s);   // personal notes like "– *koupit*" stay private
    $out = h($s);
    $out = preg_replace('/\*\*(.+?)\*\*/u', '<strong>$1</strong>', $out);
    $out = preg_replace('/(?<!\w)\*([^*]+)\*(?!\w)/u', '<em>$1</em>', $out);
    return (string)$out;
}

/** Parse our recipe Markdown into a structured array. */
function parse_recipe(string $md): array
{
    $r = ['title' => '', 'description' => '', 'servings' => null, 'total' => null, 'active' => null,
          'groups' => [], 'prep' => [], 'steps' => [], 'tips' => []];
    $section = ''; $group = '';
    foreach (preg_split('/\R/u', $md) as $raw) {
        $line = trim($raw);
        if ($line === '') continue;
        if (preg_match('/^#\s+(.+)/u', $line, $m)) { $r['title'] = plain($m[1]); continue; }
        if (preg_match('/^##\s+(.+)/u', $line, $m)) { $section = mb_strtolower(plain($m[1])); $group = ''; continue; }
        if (preg_match('/^###\s+(.+)/u', $line, $m)) { $group = plain($m[1]); continue; }
        if ($section === '') {
            if (str_contains($line, '**Porce**') || str_contains($line, '**Čas**')) {
                if (preg_match('/\*\*Porce\*\*\s*(\d+)/u', $line, $m)) $r['servings'] = (int)$m[1];
                if (preg_match('/\*\*Čas\*\*\s*(\d+)\s*min/u', $line, $m)) $r['total'] = (int)$m[1];
                if (preg_match('/aktivně\s*(\d+)\s*min/u', $line, $m)) $r['active'] = (int)$m[1];
            } elseif ($r['description'] === '') {
                $r['description'] = $line;
            }
            continue;
        }
        $isItem = preg_match('/^[-*]\s+(.+)/u', $line, $mi);
        $isStep = preg_match('/^\d+[.)]\s+(.+)/u', $line, $ms);
        if (str_starts_with($section, 'ingredience') && $isItem) $r['groups'][$group][] = $mi[1];
        elseif (str_starts_with($section, 'příprava') && $isItem) $r['prep'][] = $mi[1];
        elseif (str_starts_with($section, 'postup') && $isStep) $r['steps'][] = $ms[1];
        elseif (str_starts_with($section, 'tipy') && $isItem) $r['tips'][] = $mi[1];
    }
    return $r;
}

function page(string $title, string $body, string $head = ''): never
{
    echo '<!doctype html><html lang="cs"><head><meta charset="utf-8">'
        . '<meta name="viewport" content="width=device-width, initial-scale=1">'
        . '<meta name="robots" content="noindex, nofollow">'
        . '<title>' . h($title) . '</title>' . $head
        . '<style>
:root{--bg:#f6f7f4;--fg:#1b241f;--muted:#5b6860;--line:#d5ddd4;--accent:#2b6e52;--chip:#dcebe2;color-scheme:light}
@media (prefers-color-scheme:dark){:root{--bg:#121714;--fg:#e6ece7;--muted:#9aa8a0;--line:#2f3a33;--accent:#6cc39b;--chip:#1f3a2d;color-scheme:dark}}
body{margin:0;background:var(--bg);color:var(--fg);font:16px/1.55 system-ui,-apple-system,"Segoe UI",sans-serif}
main{max-width:720px;margin:0 auto;padding:32px max(16px,env(safe-area-inset-left)) 48px max(16px,env(safe-area-inset-right))}
h1{font-size:clamp(26px,5vw,36px);line-height:1.15;margin:0 0 8px;text-wrap:balance}
h2{font-size:13px;letter-spacing:.08em;text-transform:uppercase;color:var(--muted);margin:28px 0 8px}
h3{font-size:15px;margin:14px 0 4px}
.meta{color:var(--muted);margin:6px 0 0}
ul,ol{padding-left:1.3em;margin:0}li{margin:4px 0}
ol li::marker{color:var(--accent);font-weight:600}
strong{font-family:ui-monospace,Consolas,monospace;font-size:.9em;background:var(--chip);padding:0 5px;border-radius:5px;white-space:nowrap}
em{color:var(--muted);font-style:normal;font-size:.85em}
a{color:var(--accent)}footer{margin-top:40px;color:var(--muted);font-size:13px;border-top:1px solid var(--line);padding-top:12px}
</style></head><body><main>' . $body . '</main></body></html>';
    exit;
}

$path = (string)($_GET['p'] ?? '');
$slug = trim(preg_replace('#^/?recepty/?#', '', $path), '/');

if ($slug === '') {   // index
    $rows = [];
    foreach (glob(RECIPES . '/*.md') ?: [] as $f) {
        $s = basename($f, '.md');
        if (!preg_match('/^[a-z0-9-]+$/', $s)) continue;
        $r = parse_recipe((string)file_get_contents($f));
        $rows[] = '<li><a href="/recepty/' . h($s) . '">' . h($r['title'] ?: $s) . '</a></li>';
    }
    page('Recepty', '<h1>Recepty</h1><ul>' . ($rows ? implode('', $rows) : '<li>Zatím žádné recepty.</li>') . '</ul>');
}

if (!preg_match('/^[a-z0-9-]+$/', $slug) || !is_file(RECIPES . "/$slug.md")) {
    http_response_code(404);
    page('Recept nenalezen', '<h1>Recept nenalezen</h1><p><a href="/recepty">Všechny recepty</a></p>');
}

$file = RECIPES . "/$slug.md";
$r = parse_recipe((string)file_get_contents($file));

$ingredients = [];
foreach ($r['groups'] as $list) foreach ($list as $i) $ingredients[] = plain($i);
$instructions = [];
foreach (array_merge($r['prep'], $r['steps']) as $s) $instructions[] = ['@type' => 'HowToStep', 'text' => plain($s)];

$ld = array_filter([
    '@context' => 'https://schema.org',
    '@type' => 'Recipe',
    'name' => $r['title'],
    'description' => plain($r['description']),
    'inLanguage' => 'cs',
    'datePublished' => gmdate('Y-m-d', filemtime($file)),
    'recipeYield' => $r['servings'] ? $r['servings'] . ' porce' : null,
    'totalTime' => $r['total'] ? 'PT' . $r['total'] . 'M' : null,
    'prepTime' => $r['active'] ? 'PT' . $r['active'] . 'M' : null,
    'cookTime' => ($r['total'] && $r['active']) ? 'PT' . max(0, $r['total'] - $r['active']) . 'M' : null,
    'recipeIngredient' => $ingredients,
    'recipeInstructions' => $instructions,
    'tool' => [['@type' => 'HowToTool', 'name' => 'Thermomix TM7']],
], fn($v) => $v !== null && $v !== '' && $v !== []);

$body = '<h1>' . h($r['title']) . '</h1><p>' . inline($r['description']) . '</p><p class="meta">'
    . ($r['servings'] ? 'Porce: ' . $r['servings'] : '')
    . ($r['total'] ? ' · Celkem ' . $r['total'] . ' min' : '')
    . ($r['active'] ? ' (aktivně ' . $r['active'] . ' min)' : '') . ' · Thermomix TM7</p>';
$body .= '<h2>Ingredience</h2>';
foreach ($r['groups'] as $g => $list) {
    if ($g !== '') $body .= '<h3>' . h($g) . '</h3>';
    $body .= '<ul>' . implode('', array_map(fn($i) => '<li>' . inline($i) . '</li>', $list)) . '</ul>';
}
if ($r['prep']) $body .= '<h2>Příprava</h2><ul>' . implode('', array_map(fn($i) => '<li>' . inline($i) . '</li>', $r['prep'])) . '</ul>';
$body .= '<h2>Postup</h2><ol>' . implode('', array_map(fn($i) => '<li>' . inline($i) . '</li>', $r['steps'])) . '</ol>';
if ($r['tips']) $body .= '<h2>Tipy</h2><ul>' . implode('', array_map(fn($i) => '<li>' . inline($i) . '</li>', $r['tips'])) . '</ul>';
$body .= '<footer><a href="/recepty">Všechny recepty</a></footer>';

page($r['title'], $body, '<script type="application/ld+json">'
    . json_encode($ld, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_HEX_TAG) . '</script>');
