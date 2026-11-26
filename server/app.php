<?php
// Serves the app's static files on the public domain behind the guest cookie
// (Caddy: / -> app.php?f=index, /catalog.json -> app.php?f=catalog). Renews the cookie.
declare(strict_types=1);
require __DIR__ . '/guest_auth.php';

const FILES = [
    'index'   => ['index.html', 'text/html; charset=utf-8'],
    'catalog' => ['catalog.json', 'application/json; charset=utf-8'],
];

guest_gate(true);

// Web app manifest for the guest. An app added to the iPhone home screen keeps its own cookies,
// separate from Safari, so it has to start at the secret link: /klic/<token> sets the cookie inside
// the installed app and redirects to the shopping list. Only someone who already holds the cookie
// (and so the token) can fetch this manifest.
if (($_GET['f'] ?? '') === 'manifest') {
    $m = json_decode((string)file_get_contents(__DIR__ . '/manifest.webmanifest'), true);
    $m['start_url'] = '/klic/' . trim((string)file_get_contents(GUEST_TOKEN));
    $m['id'] = '/';
    header('Content-Type: application/manifest+json; charset=utf-8');
    header('Cache-Control: no-store');
    header('X-Robots-Tag: noindex, nofollow');
    echo json_encode($m, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

$f = FILES[(string)($_GET['f'] ?? '')] ?? null;
if ($f === null) guest_not_found();

header('Content-Type: ' . $f[1]);
header('Cache-Control: no-cache');
header('X-Robots-Tag: noindex, nofollow');
header('Referrer-Policy: same-origin');
readfile(__DIR__ . '/' . $f[0]);
