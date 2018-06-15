#import <UIKit/UIKit.h>
#import "React/RCTComponent.h"


@interface RCTMediaPlayerView : UIView


@property (nonatomic, strong) RCTDirectEventBlock onPlayerPlaying;
@property (nonatomic, strong) RCTDirectEventBlock onPlayerPaused;
@property (nonatomic, strong) RCTDirectEventBlock onPlayerProgress;
@property (nonatomic, strong) RCTDirectEventBlock onPlayerBuffering;
@property (nonatomic, strong) RCTDirectEventBlock onPlayerBufferOK;
@property (nonatomic, strong) RCTDirectEventBlock onPlayerFinished;
@property (nonatomic, strong) RCTDirectEventBlock onPlayerBufferChange;
@property (nonatomic, strong) RCTDirectEventBlock onPlaybackError;

@property (nonatomic) BOOL autoplay;
@property (nonatomic) NSString* src;
@property (nonatomic) NSString* preload; //could be "none", "metadata", "auto"
@property (nonatomic) BOOL loop;
@property (nonatomic) BOOL muted;
@property (nonatomic) BOOL ignoreSilentSwitch;


- (void)pause;
- (void)play;
- (void)stop;
- (void)seekTo: (NSTimeInterval)timeInSec;

@end