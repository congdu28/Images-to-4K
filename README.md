# Images to 4K - AI Photo Sharpening (Android Offline)

Ứng dụng Android chạy **100% Offline**, gọn nhẹ, chuyên dụng phục vụ cho người thích chụp ảnh để **làm nét ảnh bị mờ, rung tay, out-nét và nâng cấp độ phân giải lên chuẩn 4K** trực tiếp trên điện thoại.

---

## ✨ Điểm nổi bật dành cho Dân Chụp Ảnh

1. **Hoạt động hoàn toàn Offline (100% On-Device):**
   * Không gửi bất kỳ bức ảnh nào lên server/cloud. Bảo vệ tối đa sự riêng tư và bản quyền ảnh chụp.
   * Hoạt động mượt mà ở mọi nơi, kể cả khi bạn đang đi trekking, chụp ảnh ngoại cảnh không có sóng 4G/Wifi.
2. **Tận dụng phần cứng điện thoại (GPU / NPU Acceleration):**
   * Sử dụng engine **TensorFlow Lite** tích hợp **GPU Delegate**.
   * Tự động điều phối việc tính toán nơ-ron sang chip đồ họa (GPU Adreno/Mali) hoặc NPU của máy, tăng tốc độ xử lý gấp **5 – 10 lần** so với CPU thông thường.
3. **Thuật toán Tiling (Chia ô) chống tràn RAM (OOM):**
   * Cho phép xử lý ảnh chụp độ phân giải cao (12MP, 24MP, 48MP) mà không làm máy bị nóng hoặc văng ứng dụng.
4. **Bảo tồn trọn vẹn dữ liệu EXIF máy ảnh:**
   * Sau khi làm nét và nâng 4K, toàn bộ thông số chụp ảnh: **Khẩu độ (f-stop), Tốc độ màn trập (Shutter Speed), ISO, Tiêu cự (Focal Length), Dòng máy ảnh/Ống kính và Tọa độ GPS** được giữ nguyên vẹn 100%.
5. **Thanh trượt Before / After tương tác trực quan:**
   * Kéo thanh trượt 50/50 qua lại để kiểm tra độ nét.
   * Hỗ trợ cảm ứng đa điểm: **Pinch to Zoom (Phóng to đến 600%)** và kéo di chuyển để soi từng sợi lông mày, ánh mắt, chi tiết vải hay cánh hoa.
6. **3 Chế độ xử lý linh hoạt:**
   * **AI 4K (ESRGAN):** Mô hình mạng nơ-ron tái tạo chi tiết và khử nhiễu sâu, upscale kích thước lên chuẩn 4K.
   * **AI 2x (EDSR):** Mô hình siêu phân giải nhanh 2x.
   * **Pro Sharp:** Thuật toán Unsharp Masking + Micro-contrast xử lý tức thì trong 0.1 giây cho toàn bộ khung hình.

---

## 🛠 Cấu trúc Công nghệ

* **Giao diện (UI):** Kotlin + Jetpack Compose & Material 3 (Theme Dark Room phong cách Lightroom/Darkroom chuyên nghiệp).
* **AI Engine:** TensorFlow Lite Android Runtime (`org.tensorflow:tensorflow-lite`, `tensorflow-lite-gpu`).
* **Mô hình tích hợp sẵn trong APK:**
  * `esrgan_quant.tflite` (4x Super-Resolution, đã quantize nén chỉ ~5MB).
  * `edsr_quant.tflite` (2x Super-Resolution, chỉ ~1.6MB).
* **Metadata:** `androidx.exifinterface`.

---

## 🚀 Hướng dẫn Build file APK để cài lên điện thoại

### Cách 1: Tự động tạo APK qua GitHub Actions (Dễ nhất - Không cần cài đặt gì trên máy tính)
Dự án đã được cấu hình sẵn file workflow `.github/workflows/build-apk.yml`. Bạn chỉ cần:
1. Đẩy (push) mã nguồn thư mục này lên một repository cá nhân trên GitHub (Public hoặc Private đều được):
   ```bash
   git init
   git add .
   git commit -m "Images to 4K Initial Release"
   git branch -M main
   git remote add origin https://github.com/<tên-bạn>/<tên-repo>.git
   git push -u origin main
   ```
2. Vào tab **Actions** trên GitHub repository của bạn.
3. Sau 2–3 phút, quá trình build hoàn tất. Bạn vào mục **Artifacts** tải file `ImagesTo4K-Release-APK` về điện thoại và cài đặt ngay!

---

### Cách 2: Mở và Build bằng Android Studio
1. Mở **Android Studio**.
2. Chọn **Open** và trỏ đến thư mục `d:\App\Project Fun\Images to 4K`.
3. Chờ Gradle đồng bộ (Sync) xong.
4. Cắm điện thoại Android (bật USB Debugging) hoặc chọn máy ảo, sau đó nhấn nút **Run ▶️** (hoặc chọn menu `Build` -> `Build Bundle(s) / APK(s)` -> `Build APK(s)`).
5. File `.apk` sẽ nằm trong thư mục `app/build/outputs/apk/debug/app-debug.apk`.

---

## 📱 Hướng dẫn sử dụng App

1. Mở app **Images to 4K**.
2. Nhấn **"Mở Thư Viện Ảnh"** và chọn bức ảnh chụp bạn muốn phục hồi nét.
3. Xem thông số camera EXIF hiển thị ở bảng điều khiển bên dưới.
4. Chọn chế độ:
   * **AI 4K (ESRGAN)** để đạt chất lượng chi tiết cao nhất.
   * Giữ công tắc **"Tăng tốc phần cứng (GPU/NPU)"** ở trạng thái **BẬT**.
5. Nhấn **"Làm Nét 4K"** và theo dõi thanh tiến trình chia ô xử lý.
6. Sau khi xử lý xong, kéo thanh gạt ở giữa để so sánh trước/sau hoặc dùng 2 ngón tay phóng to để soi chi tiết.
7. Nhấn nút **"Lưu Ảnh 4K"** để lưu ảnh sắc nét vào bộ sưu tập (Thư mục `Pictures/ImagesTo4K`).
