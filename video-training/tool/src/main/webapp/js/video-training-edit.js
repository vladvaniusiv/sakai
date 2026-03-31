(function () {
    const sourceModeInputs = document.querySelectorAll('input[name="sourceMode"]');
    const providerTypeInput = document.getElementById('providerType');
    const externalSection = document.getElementById('externalSourceSection');
    const uploadSection = document.getElementById('uploadSourceSection');
    const resourcesSection = document.getElementById('resourcesSourceSection');
    const sourceReferenceInput = document.getElementById('sourceReference');
    const existingResourceSelect = document.getElementById('existingResourceReference');

    if (!providerTypeInput || !sourceModeInputs.length) {
        return;
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

    applyMode(selectedMode());
})();
