document.addEventListener('DOMContentLoaded', () => {
    // Header scroll effect
    const header = document.querySelector('.header');
    window.addEventListener('scroll', () => {
        if (window.scrollY > 50) {
            header.classList.add('scrolled');
        } else {
            header.classList.remove('scrolled');
        }
    });

    // Intersection Observer for fade/reveal animations
    const observerOptions = {
        root: null,
        rootMargin: '0px',
        threshold: 0.15
    };

    const observer = new IntersectionObserver((entries, observer) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.classList.add('visible');
                observer.unobserve(entry.target);
            }
        });
    }, observerOptions);

    // Observe fade-in elements
    document.querySelectorAll('.fade-in').forEach(el => {
        observer.observe(el);
    });

    // Text Reveal setup
    document.querySelectorAll('.reveal-text').forEach(el => {
        const preserveHtml = el.innerHTML;
        const words = preserveHtml.split('<br>');
        el.innerHTML = '';
        el.style.overflow = 'hidden'; // Ensure span doesn't overflow
        
        words.forEach((word, index) => {
            const span = document.createElement('span');
            span.innerHTML = word + (index < words.length - 1 ? '<br>' : '');
            if(index > 0) span.style.transitionDelay = `${index * 0.15}s`;
            el.appendChild(span);
        });
        observer.observe(el);
    });
});
