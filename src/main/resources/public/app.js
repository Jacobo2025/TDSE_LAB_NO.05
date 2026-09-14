function showError(message) {
  document.getElementById('error-area').textContent = message;
}

function clearError() {
  document.getElementById('error-area').textContent = '';
}

document.getElementById('greeting-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  clearError();
  const resultEl = document.getElementById('greeting-result');
  const name = document.getElementById('name-input').value;
  resultEl.textContent = 'Loading...';
  try {
    const res = await fetch(`/api/greeting?name=${encodeURIComponent(name)}`);
    if (!res.ok) throw new Error(`Server returned ${res.status}`);
    const data = await res.json();
    resultEl.textContent = data.greeting;
  } catch (err) {
    resultEl.textContent = '';
    showError('Could not get greeting: ' + err.message);
  }
});

document.getElementById('square-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  clearError();
  const resultEl = document.getElementById('square-result');
  const value = document.getElementById('number-input').value;
  resultEl.textContent = 'Loading...';
  try {
    const res = await fetch(`/api/square?value=${encodeURIComponent(value)}`);
    if (!res.ok) throw new Error(`Server returned ${res.status}`);
    const data = await res.json();
    resultEl.textContent = `${data.input}² = ${data.square}`;
  } catch (err) {
    resultEl.textContent = '';
    showError('Could not compute square: ' + err.message);
  }
});

document.getElementById('time-button').addEventListener('click', async () => {
  clearError();
  const resultEl = document.getElementById('time-result');
  resultEl.textContent = 'Loading...';
  try {
    const res = await fetch('/api/time');
    if (!res.ok) throw new Error(`Server returned ${res.status}`);
    const data = await res.json();
    resultEl.textContent = data.serverTime;
  } catch (err) {
    resultEl.textContent = '';
    showError('Could not get server time: ' + err.message);
  }
});