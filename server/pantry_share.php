<?php
// Read-only public pantry view for food.abhishekam.cz, gated by a secret token in the URL.
// Caddy rewrites /spiz/<token> -> /pantry_share.php?t=<token>. Wrong/absent token = 404.
// Shows only the pantry inventory (no shopping list, no editing, no API).
declare(strict_types=1);

header('Content-Type: text/html; charset=utf-8');
header('X-Robots-Tag: noindex, nofollow');
header('Referrer-Policy: no-referrer');
header('Cache-Control: no-store');

const DATA  = '/var/www/food/data/pantry.json';
const TOKEN = '/var/www/food/data/share-token.txt';

function not_found(): never
{
    http_response_code(404);
    echo '<!doctype html><html lang="cs"><head><meta charset="utf-8"><title>Nenalezeno</title>'
        . '<meta name="robots" content="noindex"></head><body style="font:16px system-ui;padding:40px">Nenalezeno.</body></html>';
    exit;
}

// --- auth: constant-time token check ---
$provided = (string)($_GET['t'] ?? '');
$secret = is_file(TOKEN) ? trim((string)file_get_contents(TOKEN)) : '';
if ($secret === '' || strlen($provided) < 16 || !hash_equals($secret, $provided)) {
    not_found();
}

// --- data ---
$CATS = [
    'Fridge' => 'Lednice', 'Freezer' => 'Mrazák', 'Pantry' => 'Spíž',
    'Fruit & veg' => 'Ovoce a zelenina', 'Spices & herbs' => 'Koření a bylinky', 'Other' => 'Ostatní',
];
$ORDER = array_keys($CATS);
$UNITS = ['g' => 'g', 'kg' => 'kg', 'ml' => 'ml', 'l' => 'l', 'pcs' => 'ks', 'pack' => 'bal.', 'tbsp' => 'lžíce', 'tsp' => 'lžička'];
$MONTHS = [1 => 'ledna', 'února', 'března', 'dubna', 'května', 'června', 'července', 'srpna', 'září', 'října', 'listopadu', 'prosince'];

$items = is_file(DATA) ? (json_decode((string)file_get_contents(DATA), true) ?: []) : [];

function h(string $s): string { return htmlspecialchars($s, ENT_QUOTES | ENT_HTML5, 'UTF-8'); }

function qtyfmt($n): string
{
    $r = round((float)$n, 2);
    $s = number_format($r, 2, ',', '');
    $s = rtrim(rtrim($s, '0'), ',');
    return $s === '' ? '0' : $s;
}

function days_until(?string $iso): ?int
{
    if (!$iso) return null;
    $d = DateTime::createFromFormat('!Y-m-d', $iso);
    if (!$d) return null;
    return (int)(new DateTime('today'))->diff($d)->format('%r%a');
}

function expiry_tag(?string $iso): string
{
    global $MONTHS;
    $n = days_until($iso);
    if ($n === null) return '';
    if ($n < 0) return '<span class="tag bad">Prošlé</span>';
    if ($n === 0) return '<span class="tag warn">Spotřebovat dnes</span>';
    if ($n === 1) return '<span class="tag warn">Spotřebovat zítra</span>';
    if ($n <= 3) return '<span class="tag warn">Spotřebovat do ' . $n . ' dnů</span>';
    [$y, $m, $day] = array_map('intval', explode('-', $iso));
    return '<span class="tag">do ' . $day . '. ' . h($MONTHS[$m] ?? (string)$m) . '</span>';
}

$unit = fn(string $u): string => $UNITS[$u] ?? $u;
$cat  = fn(string $c): string => $CATS[$c] ?? $c;

$inStock = array_values(array_filter($items, fn($i) => (float)($i['qty'] ?? 0) > 0));
$ranOut  = array_values(array_filter($items, fn($i) => (float)($i['qty'] ?? 0) <= 0));

$groups = [];
foreach ($inStock as $i) { $groups[$i['category'] ?? 'Other'][] = $i; }
uksort($groups, fn($a, $b) => array_search($a, $ORDER) <=> array_search($b, $ORDER));

$soon = 0;
foreach ($inStock as $i) { $d = days_until($i['expires'] ?? null); if ($d !== null && $d <= 3) $soon++; }

$updated = '';
foreach ($items as $i) { if (!empty($i['updatedAt']) && $i['updatedAt'] > $updated) $updated = $i['updatedAt']; }
$updatedTxt = '';
if ($updated) {
    try { $updatedTxt = (new DateTime($updated))->setTimezone(new DateTimeZone('Europe/Prague'))->format('j. n. Y H:i'); }
    catch (Exception $e) { $updatedTxt = ''; }
}
?>
<!doctype html>
<html lang="cs">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="robots" content="noindex, nofollow">
<title>Domácí spíž</title>
<style>
:root{color-scheme:light;--bg:#f3f5f1;--surface:#fff;--ink:#1b241f;--muted:#5b6860;--line:#d5ddd4;--accent:#2b6e52;--chip:#e9eee7;
--warn:#b4520f;--warn-bg:#fbe7d7;--bad:#b42318;--bad-bg:#fbe1de}
@media (prefers-color-scheme:dark){:root{--bg:#121714;--surface:#1a211d;--ink:#e6ece7;--muted:#9aa8a0;--line:#2f3a33;--accent:#6cc39b;
--chip:#222b26;--warn:#f0a062;--warn-bg:#3a2616;--bad:#f2867a;--bad-bg:#3d1c19;color-scheme:dark}}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--ink);font:16px/1.5 system-ui,-apple-system,"Segoe UI",sans-serif;
padding:24px max(16px,env(safe-area-inset-left)) 48px max(16px,env(safe-area-inset-right))}
main{max-width:720px;margin:0 auto}
h1{font-size:clamp(26px,5vw,34px);margin:0 0 4px;letter-spacing:-.02em}
.sub{color:var(--muted);font-size:14px;margin:0 0 4px}
.stats{display:flex;gap:8px;flex-wrap:wrap;margin:12px 0 4px}
.stat{background:var(--surface);border:1px solid var(--line);border-radius:999px;padding:4px 12px;font-size:13px;color:var(--muted);font-variant-numeric:tabular-nums}
.stat b{color:var(--ink)}
h2{font-size:15px;margin:26px 0 2px;display:flex;justify-content:space-between;align-items:baseline;border-bottom:1px solid var(--line);padding-bottom:6px}
h2 span{color:var(--muted);font-size:13px;font-weight:400}
.row{display:flex;justify-content:space-between;align-items:center;gap:12px;padding:8px 2px;border-bottom:1px solid var(--line)}
.name{min-width:0;overflow-wrap:anywhere}
.meta{display:flex;gap:6px;flex-wrap:wrap;margin-top:2px}
.amt{font-variant-numeric:tabular-nums;color:var(--muted);white-space:nowrap;font-size:15px}
.tag{font-size:12px;border-radius:999px;padding:1px 8px;background:var(--chip);color:var(--muted);white-space:nowrap}
.tag.warn{background:var(--warn-bg);color:var(--warn);font-weight:600}
.tag.bad{background:var(--bad-bg);color:var(--bad);font-weight:600}
.out{color:var(--muted)}.out .name{text-decoration:line-through;text-decoration-thickness:1px}
footer{margin-top:40px;color:var(--muted);font-size:13px;border-top:1px solid var(--line);padding-top:12px}
.empty{color:var(--muted);padding:28px 0}
</style>
</head>
<body>
<main>
  <h1>Domácí spíž</h1>
  <p class="sub">Co je právě doma — jen pro čtení<?= $updatedTxt ? ' · aktualizováno ' . h($updatedTxt) : '' ?></p>
  <div class="stats">
    <span class="stat"><b><?= count($inStock) ?></b> skladem</span>
    <?php if ($soon): ?><span class="stat"><b><?= $soon ?></b> spotřebovat brzy</span><?php endif; ?>
    <?php if ($ranOut): ?><span class="stat"><b><?= count($ranOut) ?></b> došlo</span><?php endif; ?>
  </div>

  <?php if (!$inStock && !$ranOut): ?>
    <p class="empty">Spíž je prázdná.</p>
  <?php endif; ?>

  <?php foreach ($groups as $c => $list): ?>
    <?php usort($list, fn($a, $b) => strcoll($a['name'], $b['name'])); ?>
    <h2><?= h($cat($c)) ?><span><?= count($list) ?></span></h2>
    <?php foreach ($list as $i): ?>
      <div class="row">
        <div class="name"><?= h($i['name']) ?><div class="meta"><?= expiry_tag($i['expires'] ?? null) ?></div></div>
        <div class="amt"><?= h(qtyfmt($i['qty'] ?? 0)) ?> <?= h($unit($i['unit'] ?? 'g')) ?></div>
      </div>
    <?php endforeach; ?>
  <?php endforeach; ?>

  <?php if ($ranOut): ?>
    <?php usort($ranOut, fn($a, $b) => strcoll($a['name'], $b['name'])); ?>
    <h2>Došlo<span><?= count($ranOut) ?></span></h2>
    <?php foreach ($ranOut as $i): ?>
      <div class="row out"><div class="name"><?= h($i['name']) ?></div><div class="amt">došlo</div></div>
    <?php endforeach; ?>
  <?php endif; ?>

  <footer>Sdílený náhled spíže. Odkaz je soukromý — kdo má odkaz, vidí obsah. Úpravy jen v domácí aplikaci.</footer>
</main>
</body>
</html>
