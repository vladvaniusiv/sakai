(function () {
	function initializeVideoDetails() {
		const videoElement = document.getElementById("vt-main-player");
		const overlay = document.getElementById("pip-overlay");
		const popupBlockedWarning = document.getElementById("pip-popup-blocked-warning");

		if (!videoElement || typeof Plyr === "undefined") {
			return;
		}

		const rawUrl = videoElement.dataset.playbackUrl || videoElement.getAttribute("src") || "";
		const contentType = videoElement.dataset.contentType || "video/mp4";

		const player = new Plyr(videoElement, {
			blankVideo: "",
			speed: { selected: 1, options: [0.5, 0.75, 1, 1.25, 1.5, 1.75, 2] },
			quality: {
				default: 720,
				options: [720, 1080],
				forced: true,
			},
		});

		if (rawUrl) {
			player.source = {
				type: "video",
				sources: [
					{ src: rawUrl, type: contentType, size: 720 },
					{ src: rawUrl, type: contentType, size: 1080 },
				],
			};
		}

		videoElement.addEventListener(
			"error",
			function () {
				videoElement.removeAttribute("crossorigin");
				if (rawUrl) {
					videoElement.src = rawUrl;
					videoElement.load();
				}
			},
			{ once: true }
		);

		const showOverlay = function () {
			if (!overlay) {
				return;
			}

			overlay.classList.add("is-visible");
			overlay.setAttribute("aria-hidden", "false");
		};

		const hideOverlay = function () {
			if (!overlay) {
				return;
			}

			overlay.classList.remove("is-visible");
			overlay.setAttribute("aria-hidden", "true");
			if (popupBlockedWarning) {
				popupBlockedWarning.classList.add("vt-is-hidden");
			}
		};

		document.addEventListener("click", function (event) {
			if (!(event.target instanceof Element)) {
				return;
			}

			const pipButton = event.target.closest('button[data-plyr="pip"]');
			if (!pipButton) {
				return;
			}

			showOverlay();

            let targetUrl = window.location.href;
            if (videoElement.dataset.portalUrl) {
                targetUrl = videoElement.dataset.portalUrl;
            } else if (window.self !== window.top) {
                try {
                    const parentUrl = window.parent.location.href;
                    if (parentUrl.includes('/portal/site/')) {
                        targetUrl = parentUrl;
                    }
                } catch (e) {
                    console.warn("VTM: The parent portal URL could not be accessed.");
                }
            }

            const popupWindow = window.open(targetUrl, "_blank");
			const popupBlocked =
				!popupWindow || popupWindow.closed || typeof popupWindow.closed === "undefined";

			if (popupBlocked && popupBlockedWarning) {
				popupBlockedWarning.classList.remove("vt-is-hidden");
			} else if (popupBlockedWarning) {
				popupBlockedWarning.classList.add("vt-is-hidden");
			}
		});

		videoElement.addEventListener("enterpictureinpicture", showOverlay);
		videoElement.addEventListener("leavepictureinpicture", hideOverlay);

		videoElement.addEventListener("webkitpresentationmodechanged", function () {
			if (videoElement.webkitPresentationMode === "picture-in-picture") {
				showOverlay();
			} else {
				hideOverlay();
			}
		});

		player.on("enterpip", showOverlay);
		player.on("exitpip", hideOverlay);
	}

	if (document.readyState === "loading") {
		document.addEventListener("DOMContentLoaded", initializeVideoDetails);
	} else {
		initializeVideoDetails();
	}
})();
