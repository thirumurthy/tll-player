(function () {
 console.log("JavaScript function executed!");

//        const divElement = document.createElement('div');
//        divElement.style.position = 'fixed';
//        divElement.style.top = '0';
//        divElement.style.left = '0';
//        divElement.style.width = '100%';
//        divElement.style.height = '100%';
//        divElement.style.backgroundColor = '#000';
//        divElement.style.zIndex = '99998';
//        document.body.appendChild(divElement);

        let count = 0;
        const interval = setInterval(() => {
const body = document.querySelector('body');
//        body.style.position = 'fixed';
//        body.style.left = '100%';
        if(body){
        body.style.backgroundColor = '#000';
         console.log("body background set!");
        }

        const sidebar= document.querySelector('#sidebar');
         if(sidebar){
         sidebar.style.display = 'none';
         console.log("sidebar set!");
         }

        const divoverlay = document.querySelector('#divoverlay');
        if(divoverlay){
        divoverlay.style.display = 'none';
        console.log("divoverlay set!");
        }

        const header = document.querySelector('.header');
        if(header){
        header.style.display = 'none';
        console.log("header set!");
        }

        const playerRight = document.querySelector('.player-right');
        if(playerRight){
        playerRight.style.display = 'none';
        console.log("player right set!");
        }

        const footer = document.querySelector('.footer');
        if(footer){
        footer.style.display = 'none';
        console.log("footer set!");
        }


        const playerContent = document.querySelector('.player-content');
        if(playerContent){
        playerContent.style.display = 'none';
        console.log("player content set!");
        }

        const searchModal = document.querySelector('#searchModal');
        if(searchModal){
        searchModal.style.display = 'none';
        console.log("searchModal set!");
        }
        const userIEModal = document.querySelector('#userIEModal');
        if(userIEModal){
        userIEModal.style.display = 'none';
        console.log("userIEModal set!");
        }
        const languageModal = document.querySelector('#languageModal');
        if(languageModal){
        languageModal.style.display = 'none';
        console.log("languageModal set!");
        }

        const userAcceptanceModal = document.querySelector('#userAcceptanceModal');
        if(userAcceptanceModal){
        userAcceptanceModal.style.display = 'none';
        console.log("userAcceptanceModal set!");
        }

        console.log("JavaScript function executed!");



        //document.querySelectorAll('#jwplayerDiv .jw-display-icon-container .jw-button-color')[0].click()
            const video = document.querySelector('video');
            if (video !== null) {

                console.log("Video is updated!");

                video.attributes.autoplay = 'true';
                video.attributes.muted = 'false';
                video.attributes.controls = 'false';
                video.style.objectFit = 'contain';
                video.style.position = 'fixed';
                video.style.width = "100vw";
                video.style.height = "100vh";
                video.style.top = '0';
                video.style.left = '0';
                video.style.zIndex = '99999';


                const images = document.querySelectorAll('img');
                const ember3 = document.querySelector('#ember3');
                if(ember3){
                ember3.style.display = 'none';
                }
                //overlayDiv
                const overlayDiv = document.querySelector('#overlayDiv');
                if(overlayDiv){
                overlayDiv.style.display = 'none';
                console.log("overlayDiv set!");
                }

                for(let i = 0; i < images.length; i++) {
                    images[i].style.display = 'none';
                }
//                var playerDiv = document.querySelectorAll('.jw-icon-fullscreen')
//                if(playerDiv){
//                playerDiv[0].click()
//                }
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


})()