
(function () {
    return new Promise(function (resolve, reject) {

        var vplayer = document.getElementById("vplayer");
        if(vplayer){
                    vplayer.click();
                }

        // Show the body after a small delay
          setTimeout(() => {
            body.style.position = '';
            body.style.left = '';
            body.style.backgroundColor = '';
          }, 100); // short delay to ensure smooth transition
        const body = document.querySelector('body');
        body.style.position = 'fixed';
        body.style.left = '100%';
        body.style.backgroundColor = '#000';
        let count = 0;
        let isPlayClickedCount =0,  isPlayBtnClicked =false;
                const interval = setInterval(() => {
                //document.querySelectorAll('#jwplayerDiv .jw-display-icon-container .jw-button-color')[0].click()

                 var jwplayer = document.getElementsByClassName("jw-icon jw-icon-display jw-button-color jw-reset")
//                 if(jwplayer.length>0 && !isPlayBtnClicked){
//                            isPlayBtnClicked = true
//                            jwplayer[0].click();
//                        }
                 var jwplayer1 = document.getElementsByClassName("jw-icon jw-icon-inline jw-button-color jw-reset jw-icon-playback")
                   if(jwplayer1.length>0 && isPlayClickedCount  <= 10){
                            isPlayClicked += 1
                            jwplayer1[0].click();
                        }
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
})();
