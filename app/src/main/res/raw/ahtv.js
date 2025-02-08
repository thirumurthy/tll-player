(function () {
    return new Promise(function (resolve, reject) {
//        const body = document.querySelector('body');
//        body.style.position = 'fixed';
//        body.style.left = '100%';
//        body.style.backgroundColor = '#000';
        const divElement = document.createElement('div');
        divElement.style.position = 'fixed';
        divElement.style.top = '0';
        divElement.style.left = '0';
        divElement.style.width = '100%';
        divElement.style.height = '100%';
        divElement.style.backgroundColor = '#000';
        divElement.style.zIndex = '9998';
        document.body.appendChild(divElement);

        let count = 0;
        const interval = setInterval(() => {
        //document.querySelectorAll('#jwplayerDiv .jw-display-icon-container .jw-button-color')[0].click()
            const video = document.querySelector('video');
            if (video !== null) {
                document.querySelectorAll('#jwplayerDiv .jw-display-icon-container .jw-button-color')[0].click()
                video.attributes.autoplay = 'true';
                video.attributes.muted = 'false';
                video.attributes.controls = 'false';
                video.style.objectFit = 'contain';
                video.style.position = 'fixed';
                video.style.width = "100vw";
                video.style.height = "100vh";
                video.style.top = '0';
                video.style.left = '0';
                video.style.zIndex = '9999';

                var navbar = document.querySelectorAll('div.nav-links')
                if(navbar.length>0){
                navbar[0].style.display = 'none';
                }
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