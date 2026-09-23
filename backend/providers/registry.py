"""
Sağlayıcı kayıt defteri. Yeni bir model/sağlayıcı eklemek için:
  1) providers/ altına VideoProvider'dan türeyen yeni bir sınıf yazın.
  2) Aşağıdaki PROVIDERS sözlüğüne bir isimle ekleyin.
main.py ve Android tarafı değişmeden yeni sağlayıcı /capabilities ve
/generate üzerinden kullanılabilir hale gelir (provider adı ileride
GENERATE isteğine opsiyonel bir alan olarak eklenebilir).
"""

from providers.wan_provider import WanProvider
from providers.cogvideox_provider import CogVideoXProvider

PROVIDERS = {
    "wan": WanProvider(),
    "cogvideox": CogVideoXProvider(),
}

DEFAULT_PROVIDER = "cogvideox"


def get_provider(name: str = DEFAULT_PROVIDER):
    provider = PROVIDERS.get(name)
    if provider is None:
        raise ValueError(f"Bilinmeyen sağlayıcı: {name}")
    return provider
