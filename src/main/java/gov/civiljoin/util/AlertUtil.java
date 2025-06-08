package gov.civiljoin.util;

/**
 * AlertUtil - Now uses in-app notifications instead of popup dialogs
 * Provides backward compatibility while using modern notification system
 */
public class AlertUtil {

    /**
     * Show an error notification (replaces error alert)
     */
    public static void showErrorAlert(String title, String content) {
        NotificationManager.getInstance().showNotification(
            title + " - " + content,
            NotificationManager.NotificationType.ERROR
        );
    }
    
    /**
     * Show a success/information notification (replaces success alert)
     */
    public static void showSuccessAlert(String title, String content) {
        NotificationManager.getInstance().showNotification(
            title + " - " + content,
            NotificationManager.NotificationType.SUCCESS
        );
    }
    
    /**
     * Show an information notification
     */
    public static void showInfoAlert(String title, String content) {
        NotificationManager.getInstance().showNotification(
            title + " - " + content,
            NotificationManager.NotificationType.INFO
        );
    }
    
    /**
     * Show a warning notification
     */
    public static void showWarningAlert(String title, String content) {
        NotificationManager.getInstance().showNotification(
            title + " - " + content,
            NotificationManager.NotificationType.WARNING
        );
    }
    
    /**
     * Show a confirmation dialog using in-app notification
     * 
     * @return true if the user confirmed, false otherwise
     */
    public static boolean showConfirmationDialog(String title, String header, String content) {
        // Use the new confirmation notification system
        final boolean[] result = {false};
        
        NotificationManager.getInstance().showConfirmation(
            title + " - " + (header != null ? header + ": " : "") + content,
            () -> result[0] = true
        );
        
        // Note: This is now asynchronous, so immediate return value may not be accurate
        // For new code, use NotificationManager.showConfirmation directly with callbacks
        return result[0];
    }
} 