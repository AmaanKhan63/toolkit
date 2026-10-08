# Current source tokens before refinement
Paper #F7F7F5, forest #1A3C2B, white surfaces; Space Grotesk titles and system body. Superseded by the requested neutral/blue light-dark direction in .superdesign/design-system.md. Existing dimensions were corrected to 48px touch targets. No standalone global CSS file exists.

## Raw configuration
```html
<script>
        tailwind.config = {
            theme: {
                extend: {
                    colors: {
                        'tk-canvas-light': '#F7F7F5',
                        'tk-surface-light': '#FFFFFF',
                        'tk-text-light': '#18251E',
                        'tk-secondary-light': '#58635C',
                        'tk-border-light': '#D9DFD9',
                        'tk-primary': '#1A3C2B',
                        'tk-on-primary': '#FFFFFF',
                        'tk-primary-container': '#E4EEE6',
                        'tk-cat-docs': '#E4EEE6',
                        'tk-cat-dl': '#F7EDC8',
                        'tk-cat-files': '#F9E2D8',
                    },
                    fontFamily: {
                        'display': ['Space Grotesk', 'sans-serif'],
                        'body': ['system-ui', '-apple-system', 'sans-serif'],
                        'mono': ['ui-monospace', 'SFMono-Regular', 'Menlo', 'Monaco', 'Consolas', 'monospace'],
                    },
                    spacing: {
                        '0': '0',
                        '4': '4px',
                        '8': '8px',
                        '12': '12px',
                        '16': '16px',
                        '20': '20px',
                        '24': '24px',
                        '32': '32px',
                    },
                    borderRadius: {
                        'tk-card': '16px',
                        'tk-btn': '12px',
                        'tk-input': '12px',
                        'tk-pill': '9999px',
                    },
                }
            }
        }
    </script>
<style>
        /* Custom scrollbar to hide it but keep functionality */
        ::-webkit-scrollbar {
            display: none;
        }
        * {
            -ms-overflow-style: none;
            scrollbar-width: none;
            -webkit-tap-highlight-color: transparent;
        }
        
        /* View Transitions */
        ::view-transition-old(root),
        ::view-transition-new(root) {
            animation-duration: 200ms;
        }
        
        .sheet-enter {
            transform: translateY(100%);
        }
        .sheet-enter-active {
            transform: translateY(0);
            transition: transform 220ms ease-out;
        }
        .sheet-exit {
            transform: translateY(0);
        }
        .sheet-exit-active {
            transform: translateY(100%);
            transition: transform 150ms ease-in;
        }
    button { min-height: 48px; min-width: 48px; }
@media (prefers-reduced-motion: reduce) { *, *::before, *::after { animation-duration: 0.01ms !important; transition-duration: 0.01ms !important; } }
main { min-height: 0; }
main > div { flex-shrink: 0; }
footer { position: relative !important; bottom: auto !important; }
</style>
```
