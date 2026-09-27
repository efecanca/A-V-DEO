from __future__ import annotations

import threading
import time
from typing import Any, Callable, Dict, Iterable, List, Optional, Protocol, Tuple

from studio.models import Capability, ProviderDescriptor


class ProviderError(RuntimeError):
    def __init__(self, message: str, *, status_code: Optional[int] = None, retryable: bool = True):
        super().__init__(message)
        self.status_code = status_code
        self.retryable = retryable


class StudioProvider(Protocol):
    descriptor: ProviderDescriptor

    def is_available(self) -> bool: ...

    def quota_state(self) -> str: ...


class NoProviderAvailable(ProviderError):
    pass


class ProviderRegistry:
    """Capability router with preference, availability, quota and cost-aware ordering."""

    def __init__(self, providers: Optional[Iterable[StudioProvider]] = None) -> None:
        self._providers: Dict[str, StudioProvider] = {}
        self._cooldowns: Dict[str, float] = {}
        self._lock = threading.Lock()
        for provider in providers or ():
            self.register(provider)

    def register(self, provider: StudioProvider) -> None:
        self._providers[provider.descriptor.name] = provider

    def public_capabilities(self) -> List[Dict[str, Any]]:
        return [
            provider.descriptor.public_dict(
                available=provider.is_available(),
                quota_state=self._effective_quota_state(provider),
            )
            for provider in sorted(
                self._providers.values(), key=lambda item: item.descriptor.priority
            )
        ]

    def candidates(
        self,
        capability: Capability,
        preferred: Optional[str] = None,
    ) -> List[StudioProvider]:
        now = time.time()
        items = [
            provider
            for provider in self._providers.values()
            if capability in provider.descriptor.capabilities
            and provider.is_available()
            and self._cooldowns.get(provider.descriptor.name, 0) <= now
            and provider.quota_state() != "exhausted"
        ]

        def sort_key(provider: StudioProvider) -> Tuple[int, int, int]:
            return (
                0 if preferred and provider.descriptor.name == preferred else 1,
                provider.descriptor.priority,
                provider.descriptor.cost_tier,
            )

        return sorted(items, key=sort_key)

    def execute(
        self,
        capability: Capability,
        operation: Callable[[StudioProvider], Any],
        *,
        preferred: Optional[str] = None,
        on_attempt: Optional[Callable[[str], None]] = None,
    ) -> Tuple[str, Any, List[str]]:
        attempts: List[str] = []
        failures: List[str] = []
        providers = self.candidates(capability, preferred)
        if not providers:
            raise NoProviderAvailable(
                f"{capability.value} için kullanılabilir provider yok. "
                "Gerekli backend secret'ını veya yerel modeli kontrol edin.",
                retryable=False,
            )

        for provider in providers:
            name = provider.descriptor.name
            attempts.append(name)
            if on_attempt:
                on_attempt(name)
            try:
                return name, operation(provider), attempts
            except ProviderError as exc:
                failures.append(f"{name}: {exc}")
                if exc.status_code == 429:
                    with self._lock:
                        self._cooldowns[name] = time.time() + 60
            except Exception as exc:
                failures.append(f"{name}: {type(exc).__name__}: {exc}")

        raise ProviderError(
            "Tüm uygun provider denemeleri başarısız oldu: " + " | ".join(failures),
            retryable=False,
        )

    def _effective_quota_state(self, provider: StudioProvider) -> str:
        if self._cooldowns.get(provider.descriptor.name, 0) > time.time():
            return "cooldown"
        return provider.quota_state()
