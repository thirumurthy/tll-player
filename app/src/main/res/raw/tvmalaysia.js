(function () {
    return new Promise(function (resolve, reject) {
        const body = document.querySelector('body');
        body.style.position = 'fixed';
        body.style.left = '100%';
        body.style.backgroundColor = '#000';
        let count = 0;
        const interval = setInterval(() => {
        $("div#adsense-center").style.display='none';
        $("button").style.display='none';
        $("div#sidebar").style.display='none';
        $("div#share").style.display='none';
        $("div#adsense-center").style.display='none';
            count ++;
            if (count > 6 * 1000) {
                clearInterval(interval);
                console.log('timeout');
            }
        }, 10);
    });
})()