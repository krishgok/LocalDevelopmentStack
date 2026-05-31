const express = require('express');

const app = express();

app.get('/health', (req, res) => res.json({ status: 'ok' }));

app.listen(8080, '0.0.0.0', () => console.log('listening on :8080'));
