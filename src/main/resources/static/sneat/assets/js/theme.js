(function () {
    const savedTheme = localStorage.getItem('skill-theme');

    if (savedTheme === 'light') {
        document.body.classList.add('light-theme');
    }
})();

function toggleTheme() {
    document.body.classList.toggle('light-theme');

    const isLight = document.body.classList.contains('light-theme');
    localStorage.setItem('skill-theme', isLight ? 'light' : 'dark');

    const icon = document.getElementById('themeIcon');
    const text = document.getElementById('themeText');

    if (icon && text) {
        icon.innerText = isLight ? '🌙' : '☀️';
        text.innerText = isLight ? 'Modo oscuro' : 'Modo claro';
    }
}