<?php

error_reporting(E_ALL);

$pass = 'hi';

/* Get the port for the WWW service. */
$service_port = 20202;

/* Get the IP address for the target host. */
$address = gethostbyname('cs.smu.ca');

/* Create a TCP/IP socket. */
$socket = socket_create(AF_INET, SOCK_STREAM, SOL_TCP);
if ($socket === false) {
    echo "socket_create() failed: reason: " . socket_strerror(socket_last_error()) . "\n";
}
socket_set_block($socket);

$result = socket_connect($socket, $address, $service_port);
if ($result === false) {
    echo "socket_connect() failed.\nReason: ($result) " . socket_strerror(socket_last_error($socket)) . "\n";
}

$salt = socket_read($socket, 2048);
$in = sha1($salt.$pass);
socket_write($socket, $in, strlen($in));

$out = socket_read($socket, 2048);
echo $out;

echo "Logging out...";
socket_write($socket, 'l', strlen('l'));
socket_close($socket);
?>

