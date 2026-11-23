<?php
// Secret guest link: food.abhishekam.cz/klic/<token>  (Caddy rewrites it to ?t=<token>).
// Valid token -> set the permanent guest cookie and redirect to the clean URL (shopping tab).
declare(strict_types=1);
require __DIR__ . '/guest_auth.php';

header('X-Robots-Tag: noindex, nofollow');
header('Referrer-Policy: no-referrer');
header('Cache-Control: no-store');

$token = (string)($_GET['t'] ?? '');
if (!guest_is_public() || !guest_valid($token)) guest_not_found();

guest_set_cookie($token);
header('Location: /#nakup', true, 302);
