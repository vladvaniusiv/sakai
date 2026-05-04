(function () {
    const tableElement = document.getElementById('vt-video-table');
    const grid = document.getElementById('vt-video-grid');
    const tableBody = document.getElementById('vt-video-table-body');
    const pagination = document.getElementById('vt-pagination');
    const loadMoreLink = document.getElementById('vt-load-more');
    let loading = false;
    let dataTable = null;
    let suppressOrderReload = false;

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

    function buildSortedReloadUrl(sortBy, sortDir, loadedCount, batchSize) {
        const params = new URLSearchParams(window.location.search);
        params.set('viewMode', tableElement.dataset.viewMode || 'table');
        params.set('q', tableElement.dataset.query || '');
        params.set('size', String(loadedCount));
        params.set('batchSize', String(batchSize));
        params.delete('offset');
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
            if (suppressOrderReload) {
                return;
            }

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

            const loadedCount = dataTable.rows().count();
            const batchSize = Number(tableElement.dataset.batchSize || tableElement.dataset.size || loadedCount || 15);
            window.location.href = buildSortedReloadUrl(sortBy, orderDir, loadedCount, batchSize);
        });
    }

    initializeTooltips(document);
    initializeThumbnailRetry(document);

    if (!loadMoreLink) {
        return;
    }

    function setLoadingState(isLoading) {
        loading = isLoading;
        loadMoreLink.classList.toggle('disabled', isLoading);
        loadMoreLink.setAttribute('aria-disabled', isLoading ? 'true' : 'false');
        if (isLoading) {
            loadMoreLink.dataset.originalText = loadMoreLink.textContent;
            loadMoreLink.textContent = '...';
        } else if (loadMoreLink.dataset.originalText) {
            loadMoreLink.textContent = loadMoreLink.dataset.originalText;
        }
    }

    loadMoreLink.addEventListener('click', async function (event) {
        event.preventDefault();
        if (loading) {
            return;
        }

        setLoadingState(true);
        try {
            const response = await fetch(loadMoreLink.href, {
                headers: { 'X-Requested-With': 'XMLHttpRequest' },
                credentials: 'same-origin'
            });
            if (!response.ok) {
                throw new Error('Failed to load next page');
            }

            const html = await response.text();
            const doc = new DOMParser().parseFromString(html, 'text/html');

            if (grid) {
                const incomingGrid = doc.getElementById('vt-video-grid');
                if (incomingGrid) {
                    incomingGrid.querySelectorAll('.vt-video-card').forEach(function (card) {
                        grid.appendChild(card);
                        initializeTooltips(card);
                        initializeThumbnailRetry(card);
                    });
                }
            }

            if (tableBody) {
                const incomingBody = doc.getElementById('vt-video-table-body');
                if (incomingBody) {
                    const incomingRows = incomingBody.querySelectorAll('tr');
                    if (dataTable) {
                        suppressOrderReload = true;
                        incomingRows.forEach(function (row) {
                            dataTable.row.add(row);
                            initializeTooltips(row);
                            initializeThumbnailRetry(row);
                        });
                        dataTable.draw(false);
                        suppressOrderReload = false;
                    } else {
                        incomingRows.forEach(function (row) {
                            tableBody.appendChild(row);
                            initializeTooltips(row);
                            initializeThumbnailRetry(row);
                        });
                    }
                }
            }

            const incomingLoadMore = doc.getElementById('vt-load-more');
            const incomingEnd = doc.getElementById('vt-pagination-end');
            if (incomingLoadMore) {
                loadMoreLink.href = incomingLoadMore.href;
            } else {
                loadMoreLink.remove();
                if (incomingEnd) {
                    pagination.appendChild(incomingEnd);
                }
            }
        } catch (error) {
            window.location.href = loadMoreLink.href;
        } finally {
            if (document.body.contains(loadMoreLink)) {
                setLoadingState(false);
            }
        }
    });
})();
