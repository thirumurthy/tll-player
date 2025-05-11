
(function () {
    return new Promise(function (resolve, reject) {

        let count = 0;
        const interval = setInterval(() => {
        //document.querySelectorAll('#jwplayerDiv .jw-display-icon-container .jw-button-color')[0].click()
            document.querySelector('#unmuteOverlay').click();
            count ++;
            if (count > 6 * 1000) {
                clearInterval(interval);
                console.log('timeout');
            }
        }, 10);
    });
})()