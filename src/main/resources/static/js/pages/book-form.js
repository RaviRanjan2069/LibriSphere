// Seller: add a new listing or edit an existing one, with a live cover preview.
import { api } from '../api.js';
import { html, render } from '../dom.js';
import { navigate } from '../router.js';
import { CATEGORIES } from '../format.js';
import { cover, loading, setTitle, showFlash } from '../components.js';

const EMPTY = { title: '', author: '', category: 'Fiction', price: '', stock: '', coverUrl: '', description: '' };

export default function bookForm(root, { params, alive }) {
    const editing = Boolean(params.id);
    setTitle(editing ? 'Edit listing' : 'List a book');

    const paint = values => {
        root.className = 'container container-form page';
        render(root, html`
            <nav class="breadcrumb" aria-label="Breadcrumb">
                <a href="/seller">Seller dashboard</a>
                <span aria-hidden="true">/</span>
                <span aria-current="page">${editing ? 'Edit listing' : 'New listing'}</span>
            </nav>
            <div class="page-header">
                <div>
                    <h1 class="page-title">${editing ? 'Edit listing' : 'List a book'}</h1>
                    <p class="page-subtitle">${editing ? 'Changes are visible to buyers as soon as you save.' : 'Add a title to your storefront. It goes live as soon as you save.'}</p>
                </div>
            </div>
            <div data-flash></div>

            <div class="form-layout">
                <form class="card card-pad" id="book-form">
                    <div class="form-group">
                        <label class="form-label" for="title">Title</label>
                        <input type="text" class="form-input" id="title" name="title" required value="${values.title}">
                    </div>
                    <div class="form-row">
                        <div class="form-group">
                            <label class="form-label" for="author">Author(s)</label>
                            <input type="text" class="form-input" id="author" name="author" required value="${values.author}">
                        </div>
                        <div class="form-group">
                            <label class="form-label" for="category">Category</label>
                            <select class="form-input" id="category" name="category">
                                ${CATEGORIES.map(c => html`<option value="${c}" ${c === values.category ? 'selected' : ''}>${c}</option>`)}
                            </select>
                        </div>
                    </div>
                    <div class="form-row">
                        <div class="form-group">
                            <label class="form-label" for="price">Price</label>
                            <div class="input-prefix">
                                <span>₹</span>
                                <input type="number" step="0.01" min="0" class="form-input" id="price" name="price" required value="${values.price}">
                            </div>
                        </div>
                        <div class="form-group">
                            <label class="form-label" for="stock">Copies in stock</label>
                            <input type="number" min="0" step="1" class="form-input" id="stock" name="stock" required value="${values.stock}">
                        </div>
                    </div>
                    <div class="form-group">
                        <label class="form-label" for="coverUrl">Cover image URL <span class="subtle">(optional)</span></label>
                        <input type="url" class="form-input" id="coverUrl" name="coverUrl" placeholder="https://covers.openlibrary.org/b/isbn/…-L.jpg" value="${values.coverUrl}">
                        <p class="form-hint">Leave blank and we'll show a typeset cover with the title and author.</p>
                    </div>
                    <div class="form-group">
                        <label class="form-label" for="description">Description</label>
                        <textarea class="form-input" id="description" name="description" rows="5" placeholder="What is the book about, and who is it for?">${values.description}</textarea>
                    </div>
                    <div class="form-actions">
                        <a href="/seller" class="btn btn-ghost">Cancel</a>
                        <button type="submit" class="btn btn-primary" id="submit-btn">${editing ? 'Save changes' : 'Publish listing'}</button>
                    </div>
                </form>

                <aside class="preview-card" aria-label="Cover preview">
                    <div class="preview-label">Preview</div>
                    <div data-preview-cover></div>
                    <div class="preview-title" id="preview-title"></div>
                    <div class="preview-meta" data-preview-author></div>
                </aside>
            </div>`);
        bind();
    };

    function bind() {
        const form = root.querySelector('#book-form');
        const value = name => form.elements[name].value.trim();
        let coverUrl = value('coverUrl');

        // The typeset fallback follows the title and author as you type; the image only reloads on leaving the URL field
        const updatePreview = () => {
            root.querySelector('#preview-title').textContent = value('title') || 'Book title';
            root.querySelector('[data-preview-author]').textContent = value('author') || 'Author';
            root.querySelector('.cover-fallback-title').textContent = value('title') || 'Book title';
            root.querySelector('.cover-fallback-author').textContent = value('author') || 'Author';
        };
        const paintCover = () => {
            render(root.querySelector('[data-preview-cover]'), cover({ title: value('title'), author: value('author'), coverUrl }));
            updatePreview();
        };
        paintCover();

        form.addEventListener('input', e => {
            if (e.target.name === 'title' || e.target.name === 'author') updatePreview();
        });
        form.elements.coverUrl.addEventListener('blur', () => {
            if (value('coverUrl') === coverUrl) return;
            coverUrl = value('coverUrl');
            paintCover();
        });

        form.addEventListener('submit', async e => {
            e.preventDefault();
            const button = root.querySelector('#submit-btn');
            button.disabled = true;
            const body = Object.fromEntries(new FormData(form));
            body.price = Number(body.price);
            body.stock = Number(body.stock);
            try {
                const book = editing
                    ? await api(`vendor/books/${Number(params.id)}`, { method: 'PUT', body })
                    : await api('vendor/books', { method: 'POST', body });
                navigate('/seller#listings', { flash: { type: 'success', text: `“${book.title}” ${editing ? 'was updated' : 'is now listed'}.` } });
            } catch (err) {
                showFlash(root, { type: 'error', text: err.message });
                button.disabled = false;
            }
        });
    }

    if (!editing) {
        paint(EMPTY);
        return;
    }
    root.className = 'container page';
    render(root, loading());
    api(`vendor/books/${Number(params.id)}`)
        .then(book => {
            if (alive()) paint(Object.fromEntries(Object.keys(EMPTY).map(k => [k, book[k] ?? ''])));
        })
        .catch(e => {
            if (alive()) navigate('/seller', { replace: true, flash: { type: 'error', text: e.message } });
        });
}
