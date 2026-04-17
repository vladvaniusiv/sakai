(function () {
    const form = document.querySelector('.vt-video-form');
    const sourceModeInputs = document.querySelectorAll('input[name="sourceMode"]');
    const providerTypeInput = document.getElementById('providerType');
    const externalSection = document.getElementById('externalSourceSection');
    const uploadSection = document.getElementById('uploadSourceSection');
    const resourcesSection = document.getElementById('resourcesSourceSection');
    const sourceReferenceInput = document.getElementById('sourceReference');
    const existingResourceSelect = document.getElementById('existingResourceReference');
    const nativeFileInput = document.getElementById('nativeFile');
    const visibilityScopeSelect = document.getElementById('visibilityScope');
    const uploadTooLargeMessage = form?.dataset.nativeUploadTooLargeMessage || '';
    const maxNativeUploadBytes = Number.parseInt(form?.dataset.nativeUploadMaxBytes || '', 10);
    const visibilityReductionConfirmMessage = form?.dataset.visibilityReductionConfirmMessage || '';
    const isEdit = form?.dataset.isEdit === 'true';
    const videoId = form?.dataset.videoId || '';
    let currentVisibilityScope = form?.dataset.initialVisibilityScope || visibilityScopeSelect?.value || 'COURSE';

    if (!form || !providerTypeInput || !sourceModeInputs.length) {
        return;
    }

    function isValidUploadFileSize(file) {
        if (!file || !Number.isFinite(maxNativeUploadBytes) || maxNativeUploadBytes <= 0) {
            return true;
        }
        return file.size <= maxNativeUploadBytes;
    }

    function validateUploadFileSize() {
        if (!nativeFileInput || selectedMode() !== 'upload' || !nativeFileInput.files || !nativeFileInput.files.length) {
            return true;
        }

        if (isValidUploadFileSize(nativeFileInput.files[0])) {
            return true;
        }

        nativeFileInput.value = '';
        if (uploadTooLargeMessage) {
            window.alert(uploadTooLargeMessage);
        }
        return false;
    }

    function selectedMode() {
        const selected = document.querySelector('input[name="sourceMode"]:checked');
        return selected ? selected.value : 'upload';
    }

    function visibilityScopeRank(scope) {
        switch (scope) {
            case 'GLOBAL':
                return 3;
            case 'COURSE':
                return 2;
            case 'LESSON':
                return 1;
            default:
                return 0;
        }
    }

    function notifyVisibilityScopeChange(fromScope, toScope) {
        const detail = {
            event: 'video.training.visibility.scope.changed',
            videoId,
            fromScope,
            toScope,
            isMoreRestrictive: visibilityScopeRank(toScope) < visibilityScopeRank(fromScope)
        };
        document.dispatchEvent(new CustomEvent('sakai-event', {
            bubbles: true,
            detail
        }));
    }

    function applyMode(mode) {
        const isExternal = mode === 'external';
        const isResources = mode === 'resources';

        if (externalSection) {
            externalSection.hidden = !isExternal;
        }
        if (uploadSection) {
            uploadSection.hidden = isExternal || isResources;
        }
        if (resourcesSection) {
            resourcesSection.hidden = !isResources;
        }

        if (sourceReferenceInput) {
            sourceReferenceInput.required = isExternal;
            if (!isExternal) {
                sourceReferenceInput.value = '';
            }
        }

        if (existingResourceSelect) {
            existingResourceSelect.required = isResources;
            if (!isResources) {
                existingResourceSelect.value = '';
            }
        }

        providerTypeInput.value = isExternal ? 'EXTERNAL' : 'NATIVE';
    }

    sourceModeInputs.forEach((input) => {
        input.addEventListener('change', () => applyMode(selectedMode()));
    });

    if (nativeFileInput) {
        nativeFileInput.addEventListener('change', validateUploadFileSize);
    }

    if (visibilityScopeSelect) {
        visibilityScopeSelect.addEventListener('change', function () {
            const nextScope = visibilityScopeSelect.value;
            const currentRank = visibilityScopeRank(currentVisibilityScope);
            const nextRank = visibilityScopeRank(nextScope);

            if (isEdit && nextRank < currentRank && visibilityReductionConfirmMessage) {
                const shouldContinue = window.confirm(visibilityReductionConfirmMessage);
                if (!shouldContinue) {
                    visibilityScopeSelect.value = currentVisibilityScope;
                    return;
                }
            }

            notifyVisibilityScopeChange(currentVisibilityScope, nextScope);
            currentVisibilityScope = nextScope;
        });
    }

    form.addEventListener('submit', (event) => {
        if (!validateUploadFileSize()) {
            event.preventDefault();
        }
    });

    applyMode(selectedMode());
})();
