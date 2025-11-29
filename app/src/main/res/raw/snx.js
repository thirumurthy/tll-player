(function () {
    return new Promise(function (resolve, reject) {
        let count = 0;
        const maxWait = 1000; // 10 seconds

        const interval = setInterval(() => {
            count++;

            // Click unmute overlay if present
            var unmuteOverlay = document.querySelector('#unmuteOverlay');
            if(unmuteOverlay && window.getComputedStyle(unmuteOverlay).display !== 'none'){
                unmuteOverlay.click();
                setTimeout(() => {
                    clearInterval(interval);
                    resolve();
                }, 100);
                return;
            }

            // Check if video is already playing with sound
            if(window.playerReady){
                var vids = document.getElementsByTagName('video');
                for (var i = 0; i < vids.length; i++) {
                    // Set video to 100% width and height
                    vids[i].style.width = '100%';
                    vids[i].style.height = '100%';
                    vids[i].style.objectFit = 'fill';

                    if (!vids[i].muted && !vids[i].paused) {
                        clearInterval(interval);
                        resolve();
                        return;
                    }
                }
            }

            if (count > maxWait) {
                clearInterval(interval);
                resolve();
            }
        }, 10);
    });
})()