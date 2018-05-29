import {NativeModules, NativeEventEmitter} from 'react-native';

export default class Downloader {

    constructor(downloadEvents) {
        this.deleteDownloadedStream = this.deleteDownloadedStream.bind(this);
        this.onDownloadProgress = this.onDownloadProgress.bind(this);
        this.onDownloadStarted = this.onDownloadStarted.bind(this);
        this.onDownloadFinished = this.onDownloadFinished.bind(this);
        this.onDownloadError = this.onDownloadError.bind(this);
        this.cancelDownload = this.cancelDownload.bind(this);
        this.downloadStream = this.downloadStream.bind(this);
        this.restoreMediaDownloader = this.restoreMediaDownloader.bind(this);

        this.downloadEvents = downloadEvents || {};

        this.downloadedItems = [];

        this.downloader = NativeModules.MediaDownloader;
        const downloaderEvent = new NativeEventEmitter(NativeModules.MediaDownloader);

        downloaderEvent.addListener('onDownloadFinished', this.onDownloadFinished);
        downloaderEvent.addListener('onDownloadProgress', this.onDownloadProgress);
        downloaderEvent.addListener('onDownloadStarted', this.onDownloadStarted);
        downloaderEvent.addListener('onDownloadError', this.onDownloadError);
    }

    restoreMediaDownloader() {
        this.downloader.restoreMediaDownloader();
    }

    downloadStream(url, downloadID, bitRate) {
        if (bitRate) {
            this.downloader.downloadStreamWithBitRate(url, downloadID, bitRate)
        } else {
            this.downloader.downloadStream(url, downloadID);
        }
    }

    deleteDownloadedStream(url) {
        this.downloader.deleteDownloadedStream(url);
    }

    cancelDownload(downloadID) {
        this.downloader.cancelDownload(downloadID);
    }

    onDownloadProgress(data) {
        if (this.downloadEvents.onDownloadProgress) this.downloadEvents.onDownloadProgress(data);
    }

    onDownloadStarted(data) {
        if (this.downloadEvents.onDownloadStarted) this.downloadEvents.onDownloadStarted(data);
    }

    onDownloadFinished(data) {
        if (this.downloadEvents.onDownloadFinished) this.downloadEvents.onDownloadFinished(data);
    }

    onDownloadError(data) {
        if (this.downloadEvents.onDownloadError) this.downloadEvents.onDownloadError(data);
    }
}