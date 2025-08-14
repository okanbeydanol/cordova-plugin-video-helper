
function VideoHelper() { }

VideoHelper.prototype.transcodeVideo = function (options, onSuccess, onError) {
    cordova.exec(onSuccess, onError, 'VideoHelper', 'transcodeVideo', [options]);
};

VideoHelper.prototype.trim = function (trimOptions, onSuccess, onError) {
    cordova.exec(onSuccess, onError, 'VideoHelper', 'trim', [trimOptions]);
};

VideoHelper.prototype.createThumbnail = function (options, onSuccess, onError) {
    cordova.exec(onSuccess, onError, 'VideoHelper', 'createThumbnail', [options]);
};

VideoHelper.prototype.getVideoInfo = function (path, onSuccess, onError) {
    cordova.exec(onSuccess, onError, 'VideoHelper', 'getVideoInfo', [path]);
};

module.exports = new VideoHelper();
module.exports.VideoHelper = module.exports;

// For ES module import support
if (typeof window !== 'undefined' && window.cordova && window.cordova.plugins) {
    window.cordova.plugins.VideoHelper = module.exports;
}
