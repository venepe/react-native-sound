
#import <AVFoundation/AVFoundation.h>

@protocol WaveformDelegate <NSObject>

@required
-(void) didUpdateWaveform:(int)intensity;

@end

@interface Waveform : NSObject <AVAudioPlayerDelegate>

@property (strong, nonatomic) AVAudioPlayer *audioPlayer;
@property (nonatomic, retain) id <WaveformDelegate> delegate;

- (void) update;

@end
