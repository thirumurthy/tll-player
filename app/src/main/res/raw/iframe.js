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
            const iframe = document.querySelector('iframe');

            if (iframe !== null) {
                let p = iframe;
                while (p) {
                  p.style.position = 'static';
                  p.style.overflow = 'visible';
                  p = p.parentElement;
                }

                Object.assign(iframe.style, {
                  position: 'fixed',
                  top: '0',
                  left: '0',
                  width: '100vw',
                  height: '100vh',
                  zIndex: '999999999',
                  border: 'none'
                });

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