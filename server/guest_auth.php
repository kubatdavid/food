<?php
// Cookie gate for the full app on the PUBLIC domain (food.abhishekam.cz).
//
// Caddy forwards public-domain requests with `X-Food-Public: 1` (it overwrites any value the
// client sends). Apache only listens on 127.0.0.1, so that header can't be forged from outside.
// Requests without it come from food.force.local (LAN / WireGuard) and are never gated.
//
// A guest gets in once through the secret link /klic/<token> (guest_login.php), which stores the
// token in a long-lived HttpOnly cookie. Every page load renews the cookie; browsers cap cookie
// lifetime (Chrome: 400 days), so renewal keeps it effectively permanent while it is used.
// Rotating /var/www/food/data/guest-token.txt locks out every guest at once.
declare(strict_types=1);

const GUEST_TOKEN   = '/var/www/food/data/guest-token.txt';
const GUEST_COOKIE  = 'food_guest';
const GUEST_MAX_AGE = 400 * 86400;

function guest_is_public(): bool
{
    return ($_SERVER['HTTP_X_FOOD_PUBLIC'] ?? '') === '1';
}

function guest_valid(string $token): bool
{
    $secret = is_file(GUEST_TOKEN) ? trim((string)file_get_contents(GUEST_TOKEN)) : '';
    return $secret !== '' && strlen($token) >= 32 && hash_equals($secret, $token);
}

function guest_set_cookie(string $token): void
{
    setcookie(GUEST_COOKIE, $token, [
        'expires'  => time() + GUEST_MAX_AGE,
        'path'     => '/',
        'secure'   => true,
        'httponly' => true,
        'samesite' => 'Lax',
    ]);
}

function guest_not_found(): never
{
    http_response_code(404);
    header('Content-Type: text/plain; charset=utf-8');
    header('Cache-Control: no-store');
    echo "Nenalezeno.\n";
    exit;
}

/** On the public domain, require a valid guest cookie (404 otherwise). Optionally renew it. */
function guest_gate(bool $renew = false): void
{
    if (!guest_is_public()) return;
    $cookie = (string)($_COOKIE[GUEST_COOKIE] ?? '');
    if (!guest_valid($cookie)) guest_not_found();
    if ($renew) guest_set_cookie($cookie);
}
