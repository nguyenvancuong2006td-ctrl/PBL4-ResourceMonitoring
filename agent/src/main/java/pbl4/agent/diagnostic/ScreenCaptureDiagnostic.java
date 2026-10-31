package pbl4.agent.diagnostic;

import dev.onvoid.webrtc.media.video.desktop.DesktopSource;
import dev.onvoid.webrtc.media.video.desktop.ScreenCapturer;

import java.util.List;

public class ScreenCaptureDiagnostic {

    public static void main(String[] args) {

        ScreenCapturer screenCapturer = null;

        try {
            System.out.println("==================================");
            System.out.println("     SCREEN CAPTURE DIAGNOSTIC");
            System.out.println("==================================");

            System.out.println("Initializing ScreenCapturer...");

            screenCapturer = new ScreenCapturer();

            System.out.println("Getting available screens...");

            List<DesktopSource> screens =
                    screenCapturer.getDesktopSources();

            if (screens == null || screens.isEmpty()) {
                System.out.println("No screen sources found.");
                return;
            }

            System.out.println("Available screens: " + screens.size());

            for (DesktopSource screen : screens) {
                System.out.println("----------------------------------");
                System.out.println("Screen ID: " + screen.id);
                System.out.println("Screen Name: " + screen.title);
            }

            System.out.println("----------------------------------");
            System.out.println("SCREEN CAPTURE TEST COMPLETED.");

        } catch (Exception e) {

            System.err.println("SCREEN CAPTURE TEST FAILED!");
            e.printStackTrace();

        } finally {

            if (screenCapturer != null) {
                screenCapturer.dispose();
                System.out.println("ScreenCapturer disposed.");
            }
        }
    }
}