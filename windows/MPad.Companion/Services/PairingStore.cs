using System.Security.Cryptography;
using System.Text.Json;
using System.IO;

namespace MPad.Companion.Services;

internal sealed class PairingStore
{
    private readonly string _path;
    private readonly Dictionary<string, string> _tokens;
    private readonly object _gate = new();

    public PairingStore()
    {
        var directory = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "MPad");
        Directory.CreateDirectory(directory);
        _path = Path.Combine(directory, "pairings.dat");
        _tokens = Load();
    }

    public byte[]? GetToken(string deviceId)
    {
        lock (_gate)
            return _tokens.TryGetValue(deviceId, out var value) ? Convert.FromBase64String(value) : null;
    }

    public void SaveToken(string deviceId, byte[] token)
    {
        lock (_gate)
        {
            _tokens[deviceId] = Convert.ToBase64String(token);
            var plain = JsonSerializer.SerializeToUtf8Bytes(_tokens);
            var protectedBytes = ProtectedData.Protect(plain, null, DataProtectionScope.CurrentUser);
            File.WriteAllBytes(_path, protectedBytes);
        }
    }

    private Dictionary<string, string> Load()
    {
        try
        {
            if (!File.Exists(_path)) return new(StringComparer.Ordinal);
            var plain = ProtectedData.Unprotect(File.ReadAllBytes(_path), null, DataProtectionScope.CurrentUser);
            return JsonSerializer.Deserialize<Dictionary<string, string>>(plain) ?? new(StringComparer.Ordinal);
        }
        catch
        {
            return new(StringComparer.Ordinal);
        }
    }
}
