use std::io::{Read, Write};
use std::net::TcpListener;

fn main() {
    let listener = TcpListener::bind("0.0.0.0:8080").expect("bind 8080");
    println!("listening on :8080");
    for stream in listener.incoming() {
        let mut stream = match stream {
            Ok(s) => s,
            Err(_) => continue,
        };
        let mut buf = [0u8; 1024];
        let _ = stream.read(&mut buf);
        let req = String::from_utf8_lossy(&buf);
        let (status, body) = if req.starts_with("GET /health") {
            ("200 OK", r#"{"status":"ok"}"#)
        } else {
            ("404 Not Found", r#"{"error":"not found"}"#)
        };
        let response = format!(
            "HTTP/1.1 {}\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
            status,
            body.len(),
            body
        );
        let _ = stream.write_all(response.as_bytes());
    }
}
