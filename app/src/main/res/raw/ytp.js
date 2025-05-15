(function () {
    return new Promise(function (resolve, reject) {

        const body = document.querySelector('body');
        body.style.position = 'fixed';
        body.style.left = '100%';
        body.style.backgroundColor = '#000';
          // Show the body after a small delay
          setTimeout(() => {
            body.style.position = '';
            body.style.left = '';
            body.style.backgroundColor = '';
          }, 100); // short delay to ensure smooth transition

        let count = 0;
        const interval = setInterval(() => {
            var btn = document.querySelector(".ytp-mute-button");
            if (btn) btn.click();


            count++;
            if (count > 6 * 1000) { // If count exceeds 6000
                clearInterval(interval);
                console.log('timeout');
            }
        }, 100);
    });
})();
