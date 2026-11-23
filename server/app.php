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

$f = FILES[(string)($_GET['f'] ?? '')] ?? null;
if ($f === null) guest_not_found();

header('Content-Type: ' . $f[1]);
header('Cache-Control: no-cache');
header('X-Robots-Tag: noindex, nofollow');
header('Referrer-Policy: same-origin');
readfile(__DIR__ . '/' . $f[0]);
