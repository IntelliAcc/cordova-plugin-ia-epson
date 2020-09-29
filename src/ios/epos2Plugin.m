#import <Cordova/CDV.h>
#import "ePOS2.h"
//#import "ePOSEasySelect.h"

NSString* ALIGN_LEFT = @"[LEFT]";
NSString* ALIGN_CENTER = @"[CENTER]";
NSString* ALIGN_RIGHT = @"[RIGHT]";
NSString* TEXT_BOLD = @"[BOLD]";
NSString* TEXT_UNDERLINE = @"[UNDERLINE]";
NSString* TEXT_NORMAL = @"[NORMAL]";
NSString* TEXT_SMALL = @"[SMALL]";
NSString* TEXT_MEDIUM = @"[MEDIUM]";
NSString* CUT_FEED = @"[CUT]";

@interface epos2Plugin: CDVPlugin <Epos2DiscoveryDelegate, Epos2PtrReceiveDelegate>
{
    Epos2Printer *printer;
    Epos2FilterOption *filterOption_;
    int printerSeries;
    int lang;
    NSString* discoverCallbackId_;
    NSString* printCallbackId_;
}
@end

@implementation epos2Plugin

- (void)pluginInitialize {
    printerSeries = EPOS2_TM_T88;
    lang = EPOS2_MODEL_ANK;
}

- (void)startDiscover:(CDVInvokedUrlCommand *)command {
    discoverCallbackId_ = command.callbackId;

    filterOption_ = nil;
    filterOption_ = [[Epos2FilterOption alloc] init];
    [filterOption_ setDeviceType:EPOS2_TYPE_PRINTER];
    filterOption_.portType = EPOS2_PORTTYPE_TCP;

    int result = [Epos2Discovery start:filterOption_ delegate:self];
    if (EPOS2_SUCCESS != result) {
        NSLog(@"error in startDiscover()");
        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Error in discovering printer."];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    } else {
        // Wait for discovery to pick up something.
//        sleep(1000);
        
        
    }
}

- (void) onDiscovery:(Epos2DeviceInfo *)deviceInfo {
    NSLog(@"onDiscovery: %@", [deviceInfo getIpAddress]);
    NSDictionary *info = @{@"ipAddress" : [deviceInfo getIpAddress],
                           @"deviceType": [NSNumber numberWithInt:[deviceInfo getDeviceType]],
                           @"target": [deviceInfo getTarget],
                           @"deviceName": [deviceInfo getDeviceName],
                           @"macAddress": [deviceInfo getMacAddress],
                           @"bdAddress": [deviceInfo getBdAddress],
                           };
    CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:info];
    [result setKeepCallbackAsBool:YES];
    [self.commandDelegate sendPluginResult:result callbackId:discoverCallbackId_];
}

- (void)stopDiscover:(CDVInvokedUrlCommand *)command {
    NSLog(@"stopDiscover()");
    int result = EPOS2_SUCCESS;

    while (YES) {
        result = [Epos2Discovery stop];

        if (result != EPOS2_ERR_PROCESSING) {
            break;
        }
    }

    if (EPOS2_SUCCESS != result) {
        NSLog(@"error in stopDiscover()");
        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Error in stop discovering printer."];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    }
}

-(void)connectPrinter:(CDVInvokedUrlCommand *)command {
    NSString* target = [command.arguments objectAtIndex:0];
    NSLog(@"connectPrinter(), ip=%@", target);

    printer = [[Epos2Printer alloc] initWithPrinterSeries:printerSeries lang:lang];

    [printer setReceiveEventDelegate:self];

    int result = EPOS2_SUCCESS;

    if (printer == nil) {
        NSLog(@"error in connectPrinter(), printer object not found");
        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Error in connect  printer. Printer object not found"];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
        return;
    }

    result = [printer connect:[NSString stringWithFormat:@"TCP:%@", target] timeout:EPOS2_PARAM_DEFAULT];
    if (result != EPOS2_SUCCESS) {
        NSLog(@"error in connectPrinter()");
        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Error in connect  printer."];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
        return;
    }

    result = [printer beginTransaction];
    if (result != EPOS2_SUCCESS) {
        NSLog(@"error in beginTransaction()");
        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Error in begin transaction."];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
        [printer disconnect];
        return;
    }

    CDVPluginResult *cordovaResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsBool:YES];
    [self.commandDelegate sendPluginResult:cordovaResult callbackId:command.callbackId];
}

- (void)disconnectPrinter:(CDVInvokedUrlCommand *)command {
    int result = EPOS2_SUCCESS;
    //    NSMutableDictionary *dict = [[NSMutableDictionary alloc] init];

    if (printer == nil) {
        return;
    }

    result = [printer endTransaction];
    if (result != EPOS2_SUCCESS) {
        NSLog(@"error in disconnectPrinter()");
        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Error in end transaction."];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
        //        [dict setObject:[NSNumber numberWithInt:result] forKey:@"result"];
        //        [dict setObject:@"endTransaction" forKey:@"method"];
        //        [self performSelectorOnMainThread:@selector(showEposErrorFromThread:) withObject:dict waitUntilDone:NO];
    }

    result = [printer disconnect];
    if (result != EPOS2_SUCCESS) {
        NSLog(@"error in disconnectPrinter()");
        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Error in disconnect printer."];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
        //        [dict setObject:[NSNumber numberWithInt:result] forKey:@"result"];
        //        [dict setObject:@"disconnect" forKey:@"method"];
        //        [self performSelectorOnMainThread:@selector(showEposErrorFromThread:) withObject:dict waitUntilDone:NO];
    }
    [self finalizeObject];

    CDVPluginResult *cordovaResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsBool:YES];
    [self.commandDelegate sendPluginResult:cordovaResult callbackId:command.callbackId];
}

- (void)finalizeObject
{
    if (printer == nil) {
        return;
    }

    [printer clearCommandBuffer];

    [printer setReceiveEventDelegate:nil];

    printer = nil;
}

- (void)onPtrReceive:(Epos2Printer *)printerObj code:(int)code status:(Epos2PrinterStatusInfo *)status printJobId:(NSString *)printJobId {
    NSLog(@"onPtrReceive");
    NSLog(@"code: %d", code);
    NSLog(@"status: %@", status);
    NSLog(@"printJobId: %@", printJobId);

    // Again, same as on Android, why are we disconnecting here?????
    //[self disconnectPrinter:nil];
}

- (void)print:(CDVInvokedUrlCommand *)command {
    printCallbackId_ = command.callbackId;

    if (printer == nil) {
        NSLog(@"error in createPrintData()");
        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Error in create data, printer object not dound."];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
        return;
    }

    NSArray *printData = command.arguments;

    int result = EPOS2_SUCCESS;

    result += [self addLines:printData];

    result += [printer addCut:EPOS2_CUT_FEED];

    if (result != EPOS2_SUCCESS) {
        return;
    }

    [self sendDataToPrinter:command.callbackId];
}

- (void)printImage:(CDVInvokedUrlCommand *)command {
        
}

- (void)printReceipt:(CDVInvokedUrlCommand *)command {
    printCallbackId_ = command.callbackId;
    
    if (printer == nil) {
        NSLog(@"printReceipt aborted, not connected");
        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Not connected to a printer"];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
        return;
    }
    
    // Get function parameters.
    NSString* image64 = [command.arguments objectAtIndex:0];
    NSArray* text = [command.arguments objectAtIndex:1];
    
    int result = EPOS2_SUCCESS;
    
    // Add image if present
    if (![image64 isEqualToString:@""]) {
        UIImage *image = [self imageFromBase64:image64];
        
        long scale = 1.5;
        long h = (long)image.size.height * scale;
        long w = (long)image.size.width * scale;
        
        [printer addImage:image x:0 y:0 width:w height:h color:EPOS2_PARAM_DEFAULT mode:EPOS2_MODE_MONO halftone:EPOS2_HALFTONE_DITHER brightness:1.0 compress:EPOS2_PARAM_DEFAULT];
    }
    
    // Process each line of text, filtering out commands and executing them
    result += [self addLines:text];

    // Perform a feed cut at the end of the receipt.
    result += [printer addCut:EPOS2_CUT_FEED];

    if (result != EPOS2_SUCCESS) {
        NSLog(@"One of the add commands failed");
        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Failed to build receipt"];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
        return;
    }

    // Send the commands to the printer for execution.
    [self sendDataToPrinter:command.callbackId];
    
}

- (int)addLines:(NSArray*) lines
{
    int result = EPOS2_SUCCESS;
    
    for (NSString* line in lines) {
        NSLog(@"%@", line);
        if ([line isEqualToString:@"\n"] || [line isEqualToString:@""]) {
            result += [printer addFeedLine:1];
        } else if ([line isEqualToString:ALIGN_LEFT]) {
            result += [printer addTextAlign:EPOS2_ALIGN_LEFT];
        } else if ([line isEqualToString:ALIGN_CENTER]) {
            result += [printer addTextAlign:EPOS2_ALIGN_CENTER];
        } else if ([line isEqualToString:ALIGN_RIGHT]) {
            result += [printer addTextAlign:EPOS2_ALIGN_RIGHT];
        } else if ([line isEqualToString:TEXT_MEDIUM]) {
            result += [printer addTextSize:2 height:2];
        } else if ([line isEqualToString:TEXT_SMALL]) {
            result += [printer addTextSize:1 height:1];
        } else if ([line isEqualToString:TEXT_BOLD]) {
            result += [printer addTextStyle:EPOS2_FALSE ul:EPOS2_FALSE em:EPOS2_TRUE color:EPOS2_PARAM_DEFAULT];
        } else if ([line isEqualToString:TEXT_UNDERLINE]) {
            result += [printer addTextStyle:EPOS2_FALSE ul:EPOS2_TRUE em:EPOS2_FALSE color:EPOS2_PARAM_DEFAULT];
        } else if ([line isEqualToString:TEXT_NORMAL]) {
            result += [printer addTextStyle:EPOS2_FALSE ul:EPOS2_FALSE em:EPOS2_FALSE color:EPOS2_PARAM_DEFAULT];
        } else if ([line isEqualToString:CUT_FEED]) {
            result += [printer addCut:EPOS2_CUT_FEED];
        } else {
            result += [printer addText:line] + [printer addFeedLine:1];
        }

        if (result != EPOS2_SUCCESS) {
            break;
        }
    }
    
    /*
     NSString* ALIGN_LEFT = @"[LEFT]";
     NSString* ALIGN_CENTER = @"[CENTER]";
     NSString* ALIGN_RIGHT = @"[RIGHT]";
     NSString* TEXT_BOLD = @"[BOLD]";
     NSString* TEXT_UNDERLINE = @"[UNDERLINE]";
     NSString* TEXT_ITALIC = @"[UNDERLINE]";
     NSString* TEXT_NORMAL = @"[NORMAL]";
     NSString* TEXT_SMALL = @"[SMALL]";
     NSString* TEXT_MEDIUM = @"[MEDIUM]";
     NSString* CUT_FEED = @"[CUT]";
     **/
    
    return result;
}

- (void)sendDataToPrinter:(NSString*) callbackId
{
    int result = EPOS2_SUCCESS;

    if (printer == nil) {
        return;
    }

    result = [printer sendData:EPOS2_PARAM_DEFAULT];
    if (result != EPOS2_SUCCESS) {
        [printer disconnect];

        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Error in print data."];
        [self.commandDelegate sendPluginResult:result callbackId:callbackId];
        return;
    } else {
        // Empty the buffer, removing the printed receipt.
        [printer clearCommandBuffer];
    }

    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsBool:YES];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:callbackId];
}

- (UIImage*)imageFromBase64:(NSString*) base64Text
{
    NSData *dataEncoded = [[NSData alloc] initWithBase64EncodedString:base64Text options:0];
    return [UIImage imageWithData:dataEncoded];
}

@end
