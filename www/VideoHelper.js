const exec = require('cordova/exec');

const VideoHelper = {
    transcodeVideo: function (args, success, error) {
        exec(success, error, "VideoHelper", "transcodeVideo", [ args ]);
    },
    getVideoInfo: function (args, success, error) {
        exec(success, error, "VideoHelper", "getVideoInfo", [ args ]);
    },
    trimVideo: function (args, success, error) {
        exec(success, error, "VideoHelper", "trimVideo", [ args ]);
    },
    createThumbnail: function (args, success, error) {
        exec(success, error, "VideoHelper", "createThumbnail", [ args ]);
    }
};
module.exports = VideoHelper;
