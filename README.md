# Game Sidebar - Floating Browser (Master Bugfix V2)

Existing Android project - **SAFE TARGETED PATCH**, not rebuilt.

## Quick Start

1. Grant overlay permission: App will prompt for `SYSTEM_ALERT_WINDOW`.
2. Tap **Open Sidebar** - appears over Free Fire.
3. Drag only via header (40dp), resize via 16dp edges, scroll webpage inside WebView.
4. Double-tap outside sidebar (300ms) → collapse to `[◀]` handle. Tap handle → reopen same page/size.
5. Address bar: type → press GO → keyboard hides, sidebar stays.

## Settings
- `Auto-hide controls during video` (default ON) - hides title/tabs/address/shortcuts on video, leaves video area max.

## Login
If site blocks embedded login: banner "এই সাইটটি নিরাপত্তার কারণে embedded browser login অনুমতি দিচ্ছে না।" + [Secure Login] → opens CustomTabs, no password stored.

## Build
```bash
git am patches/0001-Game-SideBar-V2-keyboard-Back-double-tap-collapse-vi.patch
git checkout main && git merge feature/game-sidebar-v2
./gradlew assembleDebug  # APK: app/build/outputs/apk/debug/app-debug.apk
```
CI: `.github/workflows/build-apk.yml` does same + uploads artifact.

## Architecture
Preserved: `OverlayManager`, `SidebarPanelView`, `BrowserController`, `WebViewFactory`, tabs/shortcuts/drag/resize/overlay.

Fixed via helpers: `CustomWebView`, `VideoModeManager`, `CollapseHandleView`, `OutsideDoubleTapDetector`, `LandscapeHelper`, `PrefsManager`.

See `FINAL_REPORT.md` for root causes, file list, test results, APK path.


## 100টি সিরিয়াল জেনারেটেড ইমেজ

`tools/generate_100_images.py` দিয়ে একই `make_test_sheet.py` জেনারেটর থেকে ১০০টি
আলাদা, deterministic 2048×2048 transparent PNG তৈরি করা হয়েছে। সবগুলো
`front-side` variant এবং সরাসরি import/pipeline test-এ ব্যবহারযোগ্য। পুনরায় তৈরি করতে:

```bash
python3 tools/generate_100_images.py
# সব slot ভরা সংস্করণ চাইলে:
python3 tools/generate_100_images.py --which full --out /tmp/generated-100-full
```

ক্রম অনুযায়ী ফাইলগুলো:

1. [image-001.png](docs/assets/generated-100/image-001.png)
2. [image-002.png](docs/assets/generated-100/image-002.png)
3. [image-003.png](docs/assets/generated-100/image-003.png)
4. [image-004.png](docs/assets/generated-100/image-004.png)
5. [image-005.png](docs/assets/generated-100/image-005.png)
6. [image-006.png](docs/assets/generated-100/image-006.png)
7. [image-007.png](docs/assets/generated-100/image-007.png)
8. [image-008.png](docs/assets/generated-100/image-008.png)
9. [image-009.png](docs/assets/generated-100/image-009.png)
10. [image-010.png](docs/assets/generated-100/image-010.png)
11. [image-011.png](docs/assets/generated-100/image-011.png)
12. [image-012.png](docs/assets/generated-100/image-012.png)
13. [image-013.png](docs/assets/generated-100/image-013.png)
14. [image-014.png](docs/assets/generated-100/image-014.png)
15. [image-015.png](docs/assets/generated-100/image-015.png)
16. [image-016.png](docs/assets/generated-100/image-016.png)
17. [image-017.png](docs/assets/generated-100/image-017.png)
18. [image-018.png](docs/assets/generated-100/image-018.png)
19. [image-019.png](docs/assets/generated-100/image-019.png)
20. [image-020.png](docs/assets/generated-100/image-020.png)
21. [image-021.png](docs/assets/generated-100/image-021.png)
22. [image-022.png](docs/assets/generated-100/image-022.png)
23. [image-023.png](docs/assets/generated-100/image-023.png)
24. [image-024.png](docs/assets/generated-100/image-024.png)
25. [image-025.png](docs/assets/generated-100/image-025.png)
26. [image-026.png](docs/assets/generated-100/image-026.png)
27. [image-027.png](docs/assets/generated-100/image-027.png)
28. [image-028.png](docs/assets/generated-100/image-028.png)
29. [image-029.png](docs/assets/generated-100/image-029.png)
30. [image-030.png](docs/assets/generated-100/image-030.png)
31. [image-031.png](docs/assets/generated-100/image-031.png)
32. [image-032.png](docs/assets/generated-100/image-032.png)
33. [image-033.png](docs/assets/generated-100/image-033.png)
34. [image-034.png](docs/assets/generated-100/image-034.png)
35. [image-035.png](docs/assets/generated-100/image-035.png)
36. [image-036.png](docs/assets/generated-100/image-036.png)
37. [image-037.png](docs/assets/generated-100/image-037.png)
38. [image-038.png](docs/assets/generated-100/image-038.png)
39. [image-039.png](docs/assets/generated-100/image-039.png)
40. [image-040.png](docs/assets/generated-100/image-040.png)
41. [image-041.png](docs/assets/generated-100/image-041.png)
42. [image-042.png](docs/assets/generated-100/image-042.png)
43. [image-043.png](docs/assets/generated-100/image-043.png)
44. [image-044.png](docs/assets/generated-100/image-044.png)
45. [image-045.png](docs/assets/generated-100/image-045.png)
46. [image-046.png](docs/assets/generated-100/image-046.png)
47. [image-047.png](docs/assets/generated-100/image-047.png)
48. [image-048.png](docs/assets/generated-100/image-048.png)
49. [image-049.png](docs/assets/generated-100/image-049.png)
50. [image-050.png](docs/assets/generated-100/image-050.png)
51. [image-051.png](docs/assets/generated-100/image-051.png)
52. [image-052.png](docs/assets/generated-100/image-052.png)
53. [image-053.png](docs/assets/generated-100/image-053.png)
54. [image-054.png](docs/assets/generated-100/image-054.png)
55. [image-055.png](docs/assets/generated-100/image-055.png)
56. [image-056.png](docs/assets/generated-100/image-056.png)
57. [image-057.png](docs/assets/generated-100/image-057.png)
58. [image-058.png](docs/assets/generated-100/image-058.png)
59. [image-059.png](docs/assets/generated-100/image-059.png)
60. [image-060.png](docs/assets/generated-100/image-060.png)
61. [image-061.png](docs/assets/generated-100/image-061.png)
62. [image-062.png](docs/assets/generated-100/image-062.png)
63. [image-063.png](docs/assets/generated-100/image-063.png)
64. [image-064.png](docs/assets/generated-100/image-064.png)
65. [image-065.png](docs/assets/generated-100/image-065.png)
66. [image-066.png](docs/assets/generated-100/image-066.png)
67. [image-067.png](docs/assets/generated-100/image-067.png)
68. [image-068.png](docs/assets/generated-100/image-068.png)
69. [image-069.png](docs/assets/generated-100/image-069.png)
70. [image-070.png](docs/assets/generated-100/image-070.png)
71. [image-071.png](docs/assets/generated-100/image-071.png)
72. [image-072.png](docs/assets/generated-100/image-072.png)
73. [image-073.png](docs/assets/generated-100/image-073.png)
74. [image-074.png](docs/assets/generated-100/image-074.png)
75. [image-075.png](docs/assets/generated-100/image-075.png)
76. [image-076.png](docs/assets/generated-100/image-076.png)
77. [image-077.png](docs/assets/generated-100/image-077.png)
78. [image-078.png](docs/assets/generated-100/image-078.png)
79. [image-079.png](docs/assets/generated-100/image-079.png)
80. [image-080.png](docs/assets/generated-100/image-080.png)
81. [image-081.png](docs/assets/generated-100/image-081.png)
82. [image-082.png](docs/assets/generated-100/image-082.png)
83. [image-083.png](docs/assets/generated-100/image-083.png)
84. [image-084.png](docs/assets/generated-100/image-084.png)
85. [image-085.png](docs/assets/generated-100/image-085.png)
86. [image-086.png](docs/assets/generated-100/image-086.png)
87. [image-087.png](docs/assets/generated-100/image-087.png)
88. [image-088.png](docs/assets/generated-100/image-088.png)
89. [image-089.png](docs/assets/generated-100/image-089.png)
90. [image-090.png](docs/assets/generated-100/image-090.png)
91. [image-091.png](docs/assets/generated-100/image-091.png)
92. [image-092.png](docs/assets/generated-100/image-092.png)
93. [image-093.png](docs/assets/generated-100/image-093.png)
94. [image-094.png](docs/assets/generated-100/image-094.png)
95. [image-095.png](docs/assets/generated-100/image-095.png)
96. [image-096.png](docs/assets/generated-100/image-096.png)
97. [image-097.png](docs/assets/generated-100/image-097.png)
98. [image-098.png](docs/assets/generated-100/image-098.png)
99. [image-099.png](docs/assets/generated-100/image-099.png)
100. [image-100.png](docs/assets/generated-100/image-100.png)
