> ⚠️ **CẢNH BÁO DỮ LIỆU TIỀN:** Bản Android hiện tại **chưa hỗ trợ hai điện thoại cùng sửa sổ nợ**. Tính năng đẩy toàn bộ sổ lên/kéo toàn bộ sổ từ Firestore ở bản cũ có nguy cơ ghi đè dữ liệu. Đã khóa hai thao tác đó trong mã nguồn Android hiện tại; APK đã cài trước khi cập nhật vẫn chứa mã cũ. Đừng dùng đồng bộ hai máy hoặc khôi phục Firestore trên bản APK cũ. Ảnh chỉ ở bộ nhớ từng máy; sao lưu JSON kèm ảnh ra nơi khác trước khi thay bản APK. Chỉ dùng dữ liệu thử cho đến khi có đồng bộ từng mã nợ kèm kiểm tra phiên bản và kiểm thử thật trên hai máy.\n\n# Sổ Nợ — Android Studio

Nhánh này là project Android Studio bọc giao diện **Sổ Nợ v2.5** hiện tại bằng WebView an toàn và tích hợp sẵn khung Firebase native.

## Có gì sẵn

- Giao diện Sổ Nợ v2.5 chạy local trong APK, không phụ thuộc website.
- Chụp/chọn ảnh bằng chứng từ WebView hoạt động qua Android file chooser + camera.
- Ảnh vẫn nén và lưu cục bộ, không dùng Firebase Storage.
- Firebase Authentication Google bằng Credential Manager.
- Cloud Firestore đồng bộ người nợ + mã nợ; không tải ảnh lên Firebase.
- Firestore Rules chỉ cho UID sở hữu đọc/ghi dữ liệu của chính mình.
- `google-services.json` được gắn ở build-time, người dùng không nhập API key trong app.

## Mở bằng Android Studio

1. Clone branch `android-studio` hoặc tải ZIP của branch này.
2. Nếu thiếu `gradle/wrapper/gradle-wrapper.jar`, chạy `BOOTSTRAP_WRAPPER_WINDOWS.bat` (Windows) hoặc `./bootstrap-wrapper.sh`.
3. Làm theo `FIREBASE_SETUP.md`.
4. Copy `google-services.json` vào `app/`.
5. Sync Gradle → Run.

## Package cố định

`com.nhokk63.sono`

Firebase Android app phải đăng ký đúng package này.

## Lưu ý về rủi ro dữ liệu

Firestore ở bản đầu tiên này là lớp đồng bộ dữ liệu công nợ, không phải bản sao ảnh. Trước khi dùng thật, vẫn nên xuất JSON kèm ảnh định kỳ và cất ít nhất một bản ngoài điện thoại. Đừng thử nghiệm Restore trên dữ liệu thật mà chưa backup.
