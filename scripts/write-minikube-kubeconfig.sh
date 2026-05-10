#!/usr/bin/env sh
set -eu

case "${1:-}" in
  -h|--help)
    echo "Usage: MINIKUBE_PROFILE=minikube KUBE_CONTEXT=minikube $0 [output-file]"
    exit 0
    ;;
esac

profile="${MINIKUBE_PROFILE:-minikube}"
context="${KUBE_CONTEXT:-$profile}"
output="${1:-kubeconfig/minikube}"

mkdir -p "$(dirname -- "$output")"

cluster_name="$(kubectl config view --raw --minify --context "$context" -o jsonpath='{.contexts[0].context.cluster}')"
minikube_ip="$(minikube ip -p "$profile")"
tmp="${output}.tmp"

kubectl config view --raw --flatten --minify --context "$context" > "$tmp"
kubectl --kubeconfig "$tmp" config set-cluster "$cluster_name" --server="https://${minikube_ip}:8443" >/dev/null
chmod 0600 -- "$tmp"
mv -- "$tmp" "$output"

echo "Wrote ${output} for ${context} at https://${minikube_ip}:8443"
