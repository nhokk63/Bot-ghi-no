# Thiết lập Firebase cho Sổ Nợ Android

Project này dùng Firebase **Authentication + Cloud Firestore**. Ảnh bằng chứng **không** đưa lên Firebase Storage: ảnh tiếp tục được nén và lưu trong bộ nhớ ứng dụng, đồng thời đi theo tệp JSON sao lưu có ảnh.

## 0. Thông tin cố định của project

- Android package / application ID: `com.nhokk63.sono`
- Min SDK: 23
- Compile / target SDK: 37
- Firebase config cần đặt tại: `app/google-services.json`

Đừng đổi package sau khi đăng ký Firebase, trừ khi mày tạo lại Android app tương ứng trong Firebase.

## 1. Tạo project Firebase

1. Mở Firebase Console.
2. **Create project** → đặt tên ví dụ `So No`.
3. Analytics không bắt buộc cho app này; có thể tắt.
4. Chờ Firebase tạo project xong.

## 2. Đăng ký app Android

Firebase Console → **Project settings → General → Your apps → Add app → Android**.

Nhập:

- Android package name: `com.nhokk63.sono`
- App nickname: `So No Android`

Chưa cần SHA-1 ở lần đầu nếu mày chưa có debug keystore. Đăng ký app trước.

## 3. Lấy SHA-1 / SHA-256

Sau khi project mở được bằng Android Studio:

```text
Android Studio → Gradle → app → Tasks → android → signingReport
```

Hoặc Terminal:

```bash
./gradlew signingReport
```

Windows:

```bat
gradlew.bat signingReport
```

Copy **SHA1** và nên copy luôn **SHA-256** của variant `debug`.

Firebase Console → Project settings → General → app Android `com.nhokk63.sono` → **Add fingerprint** → thêm SHA-1 và SHA-256.

## 4. Bật Google Sign-In

Firebase Console → **Authentication → Get started → Sign-in method → Google → Enable → Save**.

Sau khi bật Google và thêm SHA-1, quay lại Project settings rồi **tải lại `google-services.json` mới**. File mới phải có OAuth web client để Android nhận `default_web_client_id`.

Copy file vào đúng đường dẫn:

```text
SoNo/
└── app/
    └── google-services.json
```

Tên phải đúng `google-services.json`, không để kiểu `google-services (2).json`.

## 5. Tạo Firestore

Firebase Console → **Firestore Database → Create database**.

- Chọn **Production mode**.
- Chọn region gần Việt Nam phù hợp với project của mày.
- Tạo database.

Sau đó dùng rules trong file `firestore.rules` của project:

```text
users/{uid}/...
```

chỉ tài khoản có đúng `request.auth.uid == uid` mới đọc/ghi dữ liệu của chính nó.

Có thể deploy rules bằng Firebase CLI:

```bash
firebase login
firebase use --add
firebase deploy --only firestore:rules
```

Hoặc copy nội dung `firestore.rules` vào Firestore → Rules → Publish.

## 6. Sync project Android Studio

Nếu repo chưa có `gradle/wrapper/gradle-wrapper.jar`, trên Windows chạy:

```bat
BOOTSTRAP_WRAPPER_WINDOWS.bat
```

macOS / Linux:

```bash
chmod +x bootstrap-wrapper.sh
./bootstrap-wrapper.sh
```

Sau đó Android Studio:

1. **File → Open** → chọn thư mục project.
2. Chọn **JDK 17** nếu Android Studio hỏi Gradle JDK.
3. **Sync Project with Gradle Files**.
4. Cắm điện thoại → bật USB debugging → Run `app`.

## 7. Cách Firebase hoạt động trong app này

Giao diện vẫn là Sổ Nợ HTML/X-UI hiện tại trong WebView, nhưng Firebase là SDK Android native.

Trong **Cài đặt → Tài khoản & đăng nhập** hoặc **Firebase & đồng bộ**, project Android chặn menu placeholder của HTML và mở menu Firebase native:

- Đăng nhập Google.
- Đồng bộ dữ liệu công nợ hiện tại lên Firestore.
- Khôi phục dữ liệu công nợ từ Firestore.
- Đăng xuất.

Firestore lưu:

```text
users/{uid}/people/{personId}
users/{uid}/debts/{debtId}
users/{uid}/meta/state
```

Ảnh bằng chứng không được tải lên Firestore/Storage. Trường `hasPhoto` chỉ cho biết khoản nợ có ảnh trên thiết bị / backup hay không.

## 8. Sao lưu ảnh

- Ảnh được HTML nén trước khi lưu.
- Ảnh nằm trong IndexedDB/WebView app data trên máy.
- Sao lưu cục bộ mỗi ngày được tạo khi app có cơ hội chạy.
- Xuất **JSON kèm ảnh** để giữ bản độc lập ở Drive / USB / máy tính.

Quan trọng: nếu Android force-stop app hoặc máy tắt, WebView không thể tự chạy JavaScript để tạo backup. Muốn backup đúng giờ kể cả app bị đóng thì bước sau phải chuyển kho backup sang native Android (Room/WorkManager). Project này chưa giả vờ rằng HTML có thể làm điều đó.

## 9. Khi build bản Release

Debug SHA-1 khác Release SHA-1. Khi có keystore release:

1. Lấy SHA-1 + SHA-256 của release keystore.
2. Thêm cả hai vào Firebase Android app.
3. Tải lại `google-services.json`.
4. Build APK/AAB release.

Nếu phát hành Google Play, Play App Signing còn có certificate riêng; thêm fingerprint của **App signing key** trong Play Console vào Firebase.

## 10. Bảo mật

- Không bao giờ nhét Service Account JSON/private key vào APK.
- `google-services.json` không phải private key, nhưng repo này vẫn `.gitignore` nó để tránh nhầm cấu hình môi trường.
- Firestore Rules mới là lớp kiểm soát quyền đọc/ghi.
- Trước khi bật App Check enforcement, test kỹ bản cài ngoài Play Store; cấu hình Play Integrity sai có thể tự khoá app của mày khỏi Firebase.
