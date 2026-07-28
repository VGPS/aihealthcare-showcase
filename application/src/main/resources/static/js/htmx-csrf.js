/**
 * HTMX CSRF Bridge — wires Spring Security's CSRF token into every HTMX request.
 *
 * Spring Security requires the CSRF token on state-changing HTTP verbs (POST, PUT,
 * DELETE, PATCH). The token and its expected header name are rendered into <meta> tags
 * by the Thymeleaf head fragment. This listener picks them up and attaches the header
 * before each HTMX request fires.
 *
 * GET requests do not carry CSRF tokens and are unaffected.
 */
document.addEventListener('htmx:configRequest', function (evt) {
    var csrfToken  = document.querySelector('meta[name="_csrf"]');
    var csrfHeader = document.querySelector('meta[name="_csrf_header"]');
    if (csrfToken && csrfHeader) {
        evt.detail.headers[csrfHeader.getAttribute('content')] = csrfToken.getAttribute('content');
    }
});
