(function () {
    return new Promise(function (resolve, reject) {

        const body = document.querySelector('body');
        body.style.position = 'fixed';
        body.style.left = '100%';
        body.style.backgroundColor = '#000';
        var img = document.getElementById('test-whitelist');
        if(img){
            img.id = 'none';
        }
          // Show the body after a small delay
          setTimeout(() => {
            body.style.position = '';
            body.style.left = '';
            body.style.backgroundColor = '';
          }, 100); // short delay to ensure smooth transition

        let count = 0;
        const interval = setInterval(() => {
            // Using vanilla JavaScript to access and manipulate elements
            const adsenseCenter = document.getElementById("adsense-center");
            const anti_ab = document.getElementById("anti_ab");


            const sidebar = document.getElementById("sidebar");
            const share = document.getElementById("share");
            const button = document.querySelector("button");

            if (adsenseCenter) {
                adsenseCenter.style.display = 'none';
                adsenseCenter.style.opacity = '0';
            }
            if (sidebar) {
                sidebar.style.display = 'none';
            }
            if (share) {
                share.style.display = 'none';
            }
            if (button) {
                button.style.display = 'none';
            }

            if (anti_ab) {
                anti_ab.style.display = 'none';
                anti_ab.style.opacity = '0';
            }


            count++;
            if (count > 6 * 1000) { // If count exceeds 6000
                clearInterval(interval);
                console.log('timeout');
            }
        }, 10000);
    });
})();
