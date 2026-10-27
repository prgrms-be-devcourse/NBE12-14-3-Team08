// 인증 사진의 가로·세로 픽셀 크기. 필드 이름을 인증 등록 API 본문과 같게 두어 그대로 펼쳐서 보낼 수 있다.
export interface ImageSize {
  imageWidth: number;
  imageHeight: number;
}

// 서버가 받는 크기 범위(1 ~ 20000)
const IMAGE_SIZE_MAX = 20000;

// 읽을 수 없는 URL이 올려 보내기를 오래 붙잡지 않도록 기다리는 시간
const READ_TIMEOUT_MS = 10000;

// 서버가 받는 범위의 정수일 때만 크기로 인정한다. 아니면 null.
export function toImageSize(width: number, height: number): ImageSize | null {
  const valid = (value: number) => Number.isInteger(value) && value >= 1 && value <= IMAGE_SIZE_MAX;
  return valid(width) && valid(height) ? { imageWidth: width, imageHeight: height } : null;
}

// 사진 파일(새로 고른 사진) 또는 이미 올라간 사진의 URL에서 크기를 읽는다.
// 크기는 선택 값이라 읽지 못하면 null을 돌려주고, 업로드를 막지 않는다.
// naturalWidth/Height는 브라우저가 EXIF 회전을 적용한 뒤의 크기라 화면에 보이는 방향과 같다.
export function readImageSize(source: Blob | string): Promise<ImageSize | null> {
  return new Promise((resolve) => {
    const objectUrl = typeof source === 'string' ? null : URL.createObjectURL(source);
    const image = new Image();

    const finish = (size: ImageSize | null) => {
      window.clearTimeout(timer);
      image.onload = null;
      image.onerror = null;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
      resolve(size);
    };

    const timer = window.setTimeout(() => finish(null), READ_TIMEOUT_MS);
    image.onload = () => finish(toImageSize(image.naturalWidth, image.naturalHeight));
    image.onerror = () => finish(null);
    image.src = objectUrl ?? (source as string);
  });
}
