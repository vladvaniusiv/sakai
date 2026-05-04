(function () {
    const tableElement = document.getElementById('vt-video-table');
    let dataTable = null;

    function initializeTooltips(root) {
        if (!window.bootstrap || !window.bootstrap.Tooltip) {
            return;
        }
        const container = root || document;
        container.querySelectorAll('[data-bs-toggle="tooltip"]').forEach(function (element) {
            if (element.dataset.vtTooltipBound === 'true') {
                return;
            }
            window.bootstrap.Tooltip.getOrCreateInstance(element);
            element.dataset.vtTooltipBound = 'true';
        });
    }

    function appendCacheBust(url) {
        if (!url) {
            return url;
        }
        const separator = url.indexOf('?') >= 0 ? '&' : '?';
        return url + separator + 'vtcb=' + Date.now();
    }

    function revealFallback(mediaElement) {
        const wrapper = mediaElement.closest('.vt-video-thumb-wrap, .vt-table-thumb-wrap');
        if (!wrapper) {
            return;
        }
        mediaElement.classList.add('vt-is-hidden');
        const fallback = wrapper.querySelector('.vt-video-thumb-placeholder, .vt-table-thumb-placeholder');
        if (fallback) {
            fallback.classList.remove('vt-is-hidden');
        }
    }

    function bindThumbnailRetry(mediaElement) {
        if (mediaElement.dataset.vtThumbBound === 'true') {
            return;
        }

        mediaElement.addEventListener('error', function () {
            const retries = Number(mediaElement.dataset.vtThumbRetries || '0');
            if (retries < 1) {
                mediaElement.dataset.vtThumbRetries = String(retries + 1);
                if (mediaElement.tagName === 'VIDEO') {
                    const currentSrc = mediaElement.getAttribute('src') || mediaElement.currentSrc;
                    mediaElement.setAttribute('src', appendCacheBust(currentSrc));
                    mediaElement.load();
                } else {
                    const currentSrc = mediaElement.getAttribute('src');
                    mediaElement.setAttribute('src', appendCacheBust(currentSrc));
                }
                return;
            }

            revealFallback(mediaElement);
        });

        mediaElement.dataset.vtThumbBound = 'true';
    }

    function initializeThumbnailRetry(root) {
        const container = root || document;
        container.querySelectorAll('[data-thumb-retry="true"]').forEach(bindThumbnailRetry);
    }

    const dtSortByToIndex = {
        counter: 0,
        title: 1,
        context: 2,
        scope: 3,
        status: 4,
        lesson: 5,
        release: 6,
        retract: 7
    };

    const dtIndexToSortBy = {
        0: 'counter',
        1: 'title',
        2: 'context',
        3: 'scope',
        4: 'status',
        5: 'lesson',
        6: 'release',
        7: 'retract'
    };

    function getPageSize() {
        const currentSearch = new URLSearchParams(window.location.search);
        const fromSearch = Number(currentSearch.get('size'));
        if (Number.isFinite(fromSearch) && fromSearch > 0) {
            return fromSearch;
        }

        const sizeInput = document.querySelector('input[name="size"]');
        if (sizeInput) {
            const fromInput = Number(sizeInput.value);
            if (Number.isFinite(fromInput) && fromInput > 0) {
                return fromInput;
            }
        }

        if (tableElement) {
            const fromDataset = Number(tableElement.dataset.size || '0');
            if (Number.isFinite(fromDataset) && fromDataset > 0) {
                return fromDataset;
            }
        }

        return 15;
    }

    function buildSortedReloadUrl(sortBy, sortDir) {
        const params = new URLSearchParams(window.location.search);
        params.set('viewMode', tableElement.dataset.viewMode || 'table');
        params.set('q', tableElement.dataset.query || '');
        params.set('size', String(getPageSize()));
        params.set('offset', '0');
        params.delete('batchSize');
        params.delete('page');
        params.set('sortBy', sortBy);
        params.set('sortDir', sortDir);
        return window.location.pathname + '?' + params.toString();
    }

    if (tableElement && window.jQuery && window.jQuery.fn && window.jQuery.fn.DataTable) {
        const initialSortBy = tableElement.dataset.sortBy || 'modified';
        const initialSortDir = tableElement.dataset.sortDir === 'asc' ? 'asc' : 'desc';
        const initialOrderIndex = dtSortByToIndex[initialSortBy];
        const initialOrder = typeof initialOrderIndex === 'number' ? [[initialOrderIndex, initialSortDir]] : [];

        dataTable = window.jQuery(tableElement).DataTable({
            paging: false,
            searching: false,
            info: false,
            order: initialOrder,
            retrieve: true,
            columnDefs: [
                { orderable: true, targets: 0 }
            ]
        });

        window.jQuery(tableElement).on('order.dt', function () {
            const order = dataTable.order();
            if (!order || !order.length) {
                return;
            }

            const orderIndex = order[0][0];
            const orderDir = order[0][1] === 'asc' ? 'asc' : 'desc';
            const sortBy = dtIndexToSortBy[orderIndex];
            if (!sortBy) {
                return;
            }

            const currentSortBy = tableElement.dataset.sortBy || 'modified';
            const currentSortDir = tableElement.dataset.sortDir || 'desc';
            if (sortBy === currentSortBy && orderDir === currentSortDir) {
                return;
            }

            window.location.href = buildSortedReloadUrl(sortBy, orderDir);
        });
    }

    initializeTooltips(document);
    initializeThumbnailRetry(document);
})();
