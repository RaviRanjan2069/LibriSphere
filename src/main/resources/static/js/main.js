// Bookstore Platform Frontend Scripts

document.addEventListener('DOMContentLoaded', () => {
    initCartAddForm();
    initFlashAlerts();
    initActiveNavLink();
});

// Toast system
function showToast(message, isSuccess = true) {
    // Check if container exists, create if not
    let container = document.querySelector('.toast-container');
    if (!container) {
        container = document.createElement('div');
        container.className = 'toast-container';
        document.body.appendChild(container);
    }

    const toast = document.createElement('div');
    toast.className = 'toast';
    if (!isSuccess) {
        toast.style.borderColor = 'var(--danger)';
    }

    toast.innerHTML = `
        <span style="color: ${isSuccess ? 'var(--success)' : 'var(--danger)'}">
            ${isSuccess ? '✓' : '✗'}
        </span>
        <span>${message}</span>
    `;

    container.appendChild(toast);

    // Remove toast after 3.5 seconds
    setTimeout(() => {
        toast.style.animation = 'slideIn 0.3s reverse forwards';
        setTimeout(() => {
            toast.remove();
        }, 300);
    }, 3500);
}

// Asynchronous add to cart
function initCartAddForm() {
    const addForms = document.querySelectorAll('.add-to-cart-form');
    addForms.forEach(form => {
        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            
            const bookId = form.querySelector('input[name="bookId"]').value;
            const quantity = form.querySelector('input[name="quantity"]').value;
            
            try {
                const formData = new FormData();
                formData.append('bookId', bookId);
                formData.append('quantity', quantity);

                const response = await fetch('/cart/add', {
                    method: 'POST',
                    body: formData
                });

                if (!response.ok) {
                    throw new Error('Network response was not ok');
                }

                const result = await response.json();
                
                if (result.success) {
                    showToast(result.message, true);
                    
                    // Update cart badge in navbar
                    const cartBadge = document.querySelector('.cart-badge');
                    if (cartBadge) {
                        cartBadge.textContent = result.cartSize;
                        cartBadge.style.display = 'flex';
                    }
                } else {
                    showToast(result.message, false);
                }
            } catch (error) {
                console.error('Error adding to cart:', error);
                showToast('Failed to add book to cart. Try logging in again.', false);
            }
        });
    });
}

// Automatic dismissal of static alerts
function initFlashAlerts() {
    const alerts = document.querySelectorAll('.alert');
    alerts.forEach(alert => {
        setTimeout(() => {
            alert.style.transition = 'opacity 0.5s ease';
            alert.style.opacity = '0';
            setTimeout(() => {
                alert.remove();
            }, 500);
        }, 5000);
    });
}

// Active Nav Link Highlighter
function initActiveNavLink() {
    const currentPath = window.location.pathname;
    const navLinks = document.querySelectorAll('.nav-links .nav-link');
    navLinks.forEach(link => {
        const href = link.getAttribute('href');
        if (currentPath === href) {
            link.classList.add('active');
        } else if (href !== '/' && currentPath.startsWith(href)) {
            link.classList.add('active');
        } else {
            link.classList.remove('active');
        }
    });
}

// Helper to toggle vendor fields on registration form
function handleRoleChange(selectElement) {
    const vendorFields = document.getElementById('vendor-fields');
    if (selectElement.value === 'VENDOR') {
        vendorFields.style.display = 'block';
        vendorFields.querySelector('input').setAttribute('required', 'required');
    } else {
        vendorFields.style.display = 'none';
        vendorFields.querySelector('input').removeAttribute('required');
    }
}
