(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {

        // Highlight the sidebar link that best matches the current page.
        var path = window.location.pathname;
        var best = null;
        var bestLength = -1;

        document.querySelectorAll('.sidebar .nav-link-item[href]').forEach(function (link) {

            var target = link.getAttribute('href').split('?')[0];

            var matches = path === target || path.indexOf(target + '/') === 0;

            if (matches && target.length > bestLength) {
                best = link;
                bestLength = target.length;
            }
        });

        if (best) {
            best.classList.add('active');
        }

        // Off-canvas sidebar on small screens.
        var toggle = document.getElementById('sidebarToggle');
        var backdrop = document.querySelector('.sidebar-backdrop');

        if (toggle) {
            toggle.addEventListener('click', function () {
                document.body.classList.toggle('sidebar-open');
            });
        }

        if (backdrop) {
            backdrop.addEventListener('click', function () {
                document.body.classList.remove('sidebar-open');
            });
        }
    });
})();