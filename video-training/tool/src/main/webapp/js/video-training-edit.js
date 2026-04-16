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
    const uploadTooLargeMessage = form?.dataset.nativeUploadTooLargeMessage || '';
    const maxNativeUploadBytes = Number.parseInt(form?.dataset.nativeUploadMaxBytes || '', 10);

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

    form.addEventListener('submit', (event) => {
        if (!validateUploadFileSize()) {
            event.preventDefault();
        }
    });

    applyMode(selectedMode());
})();
