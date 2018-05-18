import {NativeModules} from 'react-native';

export default class Downloader {

    constructor() {
        this.test = this.test.bind(this);
        this.downloader = NativeModules.MediaDownloader;
    }

     test(url) {
        this.downloader.test(url);
    }
}