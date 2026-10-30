# SuperBattery ⚡

**SuperBattery** là ứng dụng Android chuyên dụng hỗ trợ theo dõi mức pin và điện áp dành riêng cho
các thiết bị Android đã can thiệp phần cứng: **độ cell pin Li-ion 3.7V (đầy ở 4.2V)** và sử dụng **mạch sạc ngoài mắc song song BMS pin** thay thế cho chuẩn pin Li-Po 3.85V/4.4V mặc định của hệ thống.

---

## 📌 Tính năng chính
* **Đo pin theo đường đặc tính điện áp (Voltage Mapping):** Tính toán và hiển thị đúng dung lượng pin dựa trên mức điện áp thực tế của cell 3.7V.
* **Cảnh báo điện áp thấp (Low Voltage Guard):** Cảnh báo khi pin chạm ngưỡng xả sâu để tránh làm chai/hỏng cell pin.
* **Biểu đồ theo dõi (History Graph):** Vẽ trực quan lịch sử dao động điện áp theo thời gian thực.
* **Bộ Widget đa dạng:** Cung cấp nhiều kích thước widget (1x1, 3x1, 5x1) hiển thị % pin chuẩn, nhiệt độ, điện áp và ngày tháng.
* **Tích hợp Hotspot & Lịch Âm:** Đếm thiết bị kết nối điểm phát sóng di động và hiển thị âm lịch Việt Nam ngay trên widget.

---

## ⚠️ Tuyên bố miễn trừ trách nhiệm (Disclaimer)
1. **An toàn phần cứng & Pin:** Việc độ chế cell pin, hàn nối mạch sạc ngoài và can thiệp phần cứng điện thoại tiềm ẩn rủi ro cháy nổ, hư hỏng linh kiện hoặc chập mạch nếu không có kiến thức kỹ thuật phù hợp. Tác giả **không chịu bất kỳ trách nhiệm nào** đối với mọi sự cố phần cứng, mất dữ liệu, chập cháy hoặc tai nạn phát sinh từ việc can thiệp thiết bị của bạn.
2. **Bản chất dự án:** Dự án này ban đầu được xây dựng cho nhu cầu cá nhân. Phần logic thuật toán và giải pháp do tác giả tự xây dựng; mã nguồn được hỗ trợ sinh và hoàn thiện bằng phương pháp hỗ trợ bởi AI (Vibe Coding).

---

## 📜 Bản quyền & Ghi nhận (Credits & License)
* **Mã nguồn ứng dụng:** Phát hành theo giấy phép [MIT License](LICENSE).
* **Thuật toán Âm lịch:** Module `VietnameseLunarCalendar.kt` sử dụng thuật toán tính Âm lịch Việt Nam của **Hồ Ngọc Đức**.

---

## 🛠️ Cài đặt & Đóng góp
Dự án được xây dựng bằng **Android Studio**, ngôn ngữ **Kotlin**, sử dụng View Binding.
Mọi đóng góp (Pull Request) nhằm tối ưu hóa bảng tra cứu điện áp (discharge curve) cho các loại cell khác nhau hoặc hoàn thiện giao diện đều rất được hoan nghênh.
