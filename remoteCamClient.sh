
ctrl_c_handler() {
        echo "QUIT"
        sudo modprobe --remove v4l2loopback
        echo "Ok"
        exit 0
    }

# Enumerate and store initial video devices
initial_devices=($(ls /dev/video*))
sudo modprobe v4l2loopback video_nr=7 card_label="My Fake Webcam" exclusive_caps=1
# Enumerate and store new video devices
new_devices=($(ls /dev/video*))
first_new_device=""
# Compare initial and new devices to find the new ones
for new_device in "${new_devices[@]}"; do
    is_new=true
    for initial_device in "${initial_devices[@]}"; do
        if [[ "$new_device" == "$initial_device" ]]; then
            is_new=false
            break
        fi
    done
    if $is_new; then
        echo "New device found: $new_device"
        if [ -z "$first_new_device" ]; then
            first_new_device="$new_device"
        fi
    fi
done
# Print the first new device found again
if [ -n "$first_new_device" ]; then
    echo "First new device: $first_new_device"
    
    trap ctrl_c_handler SIGINT

    ffmpeg -hide_banner -reconnect 1 -reconnect_at_eof 1   -reconnect_streamed 1 -timeout 30000000  -stream_loop -1   -f mjpeg  -i "http://192.168.1.2:8080/cam.mjpeg" -vf "format=yuv420p, transpose=1, fps=30"  -f v4l2 $first_new_device
fi
sudo modprobe --remove v4l2loopback

#-vf "format=yuv420p, transpose=1" -r 30 -c:v copy  -pix_fmt yuyv422 ,hflip
