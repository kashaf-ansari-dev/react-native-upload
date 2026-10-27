#import <Foundation/Foundation.h>
#import <React/RCTBridgeModule.h>

NS_ASSUME_NONNULL_BEGIN

@interface RNMultipartUploader : NSObject

+ (instancetype)sharedInstance;

- (void)startUploadWithOptions:(NSDictionary *)options
                      resolver:(RCTPromiseResolveBlock)resolve
                      rejecter:(RCTPromiseRejectBlock)reject
                  eventEmitter:(void (^)(NSString *eventName, id body))eventEmitter;

- (BOOL)cancelUpload:(NSString *)uploadId;
- (BOOL)pauseUpload:(NSString *)uploadId;
- (BOOL)resumeUpload:(NSString *)uploadId;

@end

NS_ASSUME_NONNULL_END
