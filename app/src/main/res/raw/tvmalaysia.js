(function () {
    'use strict';

    const observer = new MutationObserver(() => {
        // Check for the iframe
        const iframe = document.querySelector('iframe');

        if (iframe) {
            try {
                // Access the iframe's document
                const iframeDocument = iframe.contentDocument || iframe.contentWindow.document;

                // Look for the span element inside the iframe
                const verificationPhrase = iframeDocument?.querySelector('span.cb-lb-t');

                if (verificationPhrase) {
                    verificationPhrase.click();
                    console.log('Clicked verification phrase');
                    observer.disconnect(); // Stop observing after finding the iframe
                }
            } catch (error) {
                console.error('Error accessing iframe content:', error);
            }
        }
    });

    // Observe DOM changes
    observer.observe(document.body, { childList: true, subtree: true });
})();


//(function () {
//    return new Promise(function (resolve, reject) {
////        const body = document.querySelector('body');
////        body.style.position = 'fixed';
////        body.style.left = '100%';
////        body.style.backgroundColor = '#000';
//        const divElement = document.createElement('div');
//        divElement.style.position = 'fixed';
//        divElement.style.top = '0';
//        divElement.style.left = '0';
//        divElement.style.width = '100%';
//        divElement.style.height = '100%';
//        divElement.style.backgroundColor = '#000';
//        divElement.style.zIndex = '9998';
//        document.body.appendChild(divElement);
//
//        let count = 0;
//        const interval = setInterval(() => {
//        //document.querySelectorAll('#jwplayerDiv .jw-display-icon-container .jw-button-color')[0].click()
//            const video = document.querySelector('video');
//            if (video !== null) {
//                //document.querySelectorAll('.shaka-small-play-button')[0].click()
//                //document.querySelectorAll('.shaka-fullscreen-button')[0].click()
//                video.attributes.autoplay = 'true';
//                video.attributes.muted = 'false';
//                video.attributes.controls = 'false';
//                video.style.objectFit = 'contain';
//                video.style.position = 'fixed';
//                video.style.width = "100vw";
//                video.style.height = "100vh";
//                video.style.top = '0';
//                video.style.left = '0';
//                video.style.zIndex = '9999';
//
//
//                const images = document.querySelectorAll('img');
//                for(let i = 0; i < images.length; i++) {
//                    images[i].style.display = 'none';
//                }
//                clearInterval(interval);
//                setTimeout(function () {
//                    console.log('success');
//                }, 0)
//            }
//            count ++;
//            if (count > 6 * 1000) {
//                clearInterval(interval);
//                console.log('timeout');
//            }
//        }, 10);
//    });
//})()