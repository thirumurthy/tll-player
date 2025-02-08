(function () {
    return new Promise(function (resolve, reject) {
//        const divElement = document.createElement('div');
//        divElement.style.position = 'fixed';
//        divElement.style.top = '0';
//        divElement.style.left = '0';
//        divElement.style.width = '100%';
//        divElement.style.height = '100%';
//        divElement.style.backgroundColor = '#000';
//        divElement.style.zIndex = '9998';
//        document.body.appendChild(divElement);

        let count = 0;
        const interval = setInterval(() => {
            const video = document.querySelector('video');
            if (video !== null) {

                 window.player.unmute()
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

                const images = document.querySelectorAll('img');
                for(let i = 0; i < images.length; i++) {
                    images[i].style.display = 'none';
                }
                clearInterval(interval);
                setTimeout(function () {
                    console.log('success');
                }, 0)
            }
            count ++;
            if (count > 6 * 1000) {
                clearInterval(interval);
                console.log('timeout');
            }
        }, 10);
    });
})()