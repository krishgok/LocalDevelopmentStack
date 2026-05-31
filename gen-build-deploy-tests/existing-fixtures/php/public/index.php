<?php
$path = parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH);
if ($path === '/health') {
    header('Content-Type: application/json');
    echo '{"status":"ok"}';
    return;
}
http_response_code(404);
header('Content-Type: application/json');
echo '{"error":"not found"}';
