(function () {
    const tableElement = document.getElementById('vt-video-table');
    const grid = document.getElementById('vt-video-grid');
    const tableBody = document.getElementById('vt-video-table-body');
    const pagination = document.getElementById('vt-pagination');
    const loadMoreLink = document.getElementById('vt-load-more');
    let loading = false;
    let dataTable = null;
    let suppressOrderReload = false;

    const dtSortByToIndex = {
        title: 0,
        context: 1,
        scope: 2,
        status: 3,
        lesson: 4,
        release: 5,
        retract: 6
    };

    const dtIndexToSortBy = {
        0: 'title',
        1: 'context',
        2: 'scope',
        3: 'status',
        4: 'lesson',
        5: 'release',
        6: 'retract'
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
            retrieve: true
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
                        });
                        dataTable.draw(false);
                        suppressOrderReload = false;
                    } else {
                        incomingRows.forEach(function (row) {
                            tableBody.appendChild(row);
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
